package io.github.stlahxm.markdownsanitizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic protect-transform-restore primitive.
 *
 * <p>Some regex-based cleanup passes are destructive to spans that happen to
 * look like the pattern being fixed (e.g. a {@code **bold**} normalizer
 * mangling {@code **} that appears inside a LaTeX math span like
 * {@code $a^{**}$}). This lets a transform temporarily swap out matched
 * spans for an opaque placeholder, run its cleanup, then restore the
 * original text untouched.
 *
 * <p>It's also used publicly ({@code MarkdownSanitizer.clean(text, protectPatterns)})
 * so callers can protect their own domain-specific tokens (custom wiki
 * syntax, template placeholders, etc.) from the built-in cleanup passes.
 *
 * <p>Each call gets its own numeric namespace so nested calls (emphasis
 * normalization protecting math spans while already running inside an
 * outer caller-supplied protect call) can never mistake one another's
 * placeholders for their own and wipe them out during restore -- this is
 * a real bug that existed in the Python sibling of this library before
 * being fixed there; ported here already fixed.
 */
final class PlaceholderProtector {

    private static final String PREFIX = "MDSAN";
    private static final AtomicInteger CALL_COUNTER = new AtomicInteger();

    private PlaceholderProtector() {
    }

    static String protectAndRestore(String text, Pattern pattern, Function<String, String> transform) {
        int callId = CALL_COUNTER.getAndIncrement();
        // The "@@" wrapper is required, not decorative: without an
        // unambiguous non-digit boundary, a placeholder immediately
        // followed by a literal digit in the source text (e.g. protecting
        // "$5...$10" leaves "...MDSAN0_010" once the transform runs) makes
        // the restore regex's \d+ greedily swallow that trailing digit into
        // the index, producing an out-of-range index and leaking the raw
        // placeholder into the output. Found via
        // clean("costs $5 and $10, so**buy**it") leaking "MDSAN0_010"
        // verbatim into the result.
        String tokenPrefix = "@@" + PREFIX + callId + "_";
        Pattern placeholderPattern = Pattern.compile(Pattern.quote(tokenPrefix) + "(\\d+)@@");
        List<String> segments = new ArrayList<>();

        Matcher matcher = pattern.matcher(text);
        StringBuilder protectedText = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            protectedText.append(text, lastEnd, matcher.start());
            protectedText.append(tokenPrefix).append(segments.size()).append("@@");
            segments.add(matcher.group());
            lastEnd = matcher.end();
        }
        protectedText.append(text, lastEnd, text.length());

        String transformed = transform.apply(protectedText.toString());

        Matcher restoreMatcher = placeholderPattern.matcher(transformed);
        StringBuilder result = new StringBuilder();
        int restoreLastEnd = 0;
        while (restoreMatcher.find()) {
            result.append(transformed, restoreLastEnd, restoreMatcher.start());
            int index = Integer.parseInt(restoreMatcher.group(1));
            result.append(index >= 0 && index < segments.size() ? segments.get(index) : restoreMatcher.group());
            restoreLastEnd = restoreMatcher.end();
        }
        result.append(transformed, restoreLastEnd, transformed.length());
        return result.toString();
    }

    static String protectManyAndRestore(String text, List<Pattern> patterns, Function<String, String> transform) {
        if (patterns.isEmpty()) {
            return transform.apply(text);
        }
        return chain(patterns, 0, text, transform);
    }

    private static String chain(List<Pattern> patterns, int index, String value, Function<String, String> transform) {
        if (index >= patterns.size()) {
            return transform.apply(value);
        }
        return protectAndRestore(value, patterns.get(index), v -> chain(patterns, index + 1, v, transform));
    }
}
