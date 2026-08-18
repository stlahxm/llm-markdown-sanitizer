package io.github.stlahxm.markdownsanitizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fixes {@code **bold**} (and {@code ***bold italic***}) spans that LLMs
 * glue directly onto surrounding words, e.g.
 * {@code **Note**this breaks rendering} -&gt;
 * {@code **Note** this breaks rendering}.
 *
 * <p>Protects inline math spans ({@code $...$} / {@code $$...$$}) from
 * being touched, since {@code **}/{@code ***} can legitimately appear
 * inside LaTeX there.
 */
final class EmphasisFixer {

    private static final Pattern MATH_SPAN = Pattern.compile("\\$\\$?[\\s\\S]*?\\$\\$?");
    // "$...$" isn't part of the CommonMark/GFM spec -- it's a convention
    // some renderers interpret as LaTeX math, with no authoritative
    // grammar to tell "real" math apart from two unrelated dollar amounts
    // on the same line (e.g. "costs $5 and $10" would otherwise be
    // treated as one math span spanning both). Real math essentially
    // never contains two bare, plain-language words separated by nothing
    // but whitespace (LaTeX commands are `\text{...}`-wrapped or
    // separated by operators/braces, not bare spaces), so two or more
    // such words in a row is a strong signal the candidate is actually
    // prose with incidental dollar signs, not math -- declined rather
    // than protected. This is a heuristic, not a parser -- it won't be
    // right for every input, but it's strictly better than treating every
    // "$" pair as math.
    private static final Pattern PROSE_LIKE = Pattern.compile("[A-Za-z가-힣]{2,}\\s+[A-Za-z가-힣]{2,}");

    private EmphasisFixer() {
    }

    private static boolean looksLikeMath(String span) {
        String inner = span.replaceAll("^\\$+|\\$+$", "");
        return !PROSE_LIKE.matcher(inner).find();
    }

    static String normalizeBoundaries(String line) {
        List<String> realMathSpans = new ArrayList<>();
        Matcher matcher = MATH_SPAN.matcher(line);
        while (matcher.find()) {
            String span = matcher.group();
            if (looksLikeMath(span)) {
                realMathSpans.add(span);
            }
        }

        if (realMathSpans.isEmpty()) {
            return normalizeTokens(line);
        }

        // Protect only the specific spans that passed the math-likeness
        // check above, not every "$...$" candidate -- built as a precise
        // alternation of this line's actual accepted spans rather than
        // re-running the broad, greedy MATH_SPAN pattern directly.
        String alternation = realMathSpans.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        Pattern onlyRealMath = Pattern.compile(alternation);
        return PlaceholderProtector.protectAndRestore(line, onlyRealMath, EmphasisFixer::normalizeTokens);
    }

    /**
     * Whether {@code value} (a single character, or {@code ""} for "no
     * character here") would visually/parseably glue onto a {@code **}
     * marker if left touching it. {@link Character#isLetterOrDigit} is
     * Unicode-aware, so this treats Korean, CJK, and accented Latin
     * characters the same as plain ASCII letters/digits with no per-
     * language special-casing -- and correctly leaves emoji, symbols, and
     * punctuation alone, since those don't have the same gluing problem.
     *
     * <p>Note: {@code value} here is built from a single {@code char}
     * (UTF-16 code unit), not a full code point, so a lone surrogate half
     * of a supplementary-plane character would be classified as "not
     * alnum" rather than crash -- a safe default, just not exact, for the
     * extremely rare case of an emphasis boundary sitting exactly on such
     * a character.
     */
    private static boolean isAlnumBoundary(String value) {
        if (value.isEmpty()) {
            return false;
        }
        return Character.isLetterOrDigit(value.codePointAt(0));
    }

    /**
     * Which emphasis marker (if any) starts at {@code index}. {@code ***}
     * is checked before {@code **} since it's the longer marker and
     * {@code **} is a prefix of it -- checking in the other order would
     * always match the {@code **} case first and never recognize a triple
     * asterisk. Returns {@code null} if neither starts here.
     */
    private static String markerAt(String text, int index) {
        if (text.startsWith("***", index)) {
            return "***";
        }
        if (text.startsWith("**", index)) {
            return "**";
        }
        return null;
    }

    /**
     * Single left-to-right scan: copy characters through unchanged until a
     * {@code **} or {@code ***} marker is found, then look for a matching
     * same-width close on the rest of the (already math-span-protected)
     * string. A complete pair gets re-emitted with a boundary space added
     * on whichever side(s) would otherwise glue onto adjacent alphanumeric
     * text. An unmatched marker is copied through literally rather than
     * dropped (see the comment below).
     */
    private static String normalizeTokens(String text) {
        StringBuilder output = new StringBuilder();
        int index = 0;
        int length = text.length();

        while (index < length) {
            String marker = markerAt(text, index);
            if (marker == null) {
                output.appendCodePoint(text.codePointAt(index));
                index += Character.charCount(text.codePointAt(index));
                continue;
            }

            int closeIndex = text.indexOf(marker, index + marker.length());
            if (closeIndex == -1) {
                // No matching close on this line -- this isn't emphasis at all
                // (could be Java varargs-adjacent syntax, Python's `**kwargs`
                // in an embedded code sample, or just a stray typo). Keep the
                // literal characters instead of silently dropping them; only
                // complete same-width marker...marker pairs get normalized.
                output.append(marker);
                index += marker.length();
                continue;
            }

            String inner = text.substring(index + marker.length(), closeIndex).strip();
            if (!inner.isEmpty()) {
                String previous = output.length() > 0 ? String.valueOf(output.charAt(output.length() - 1)) : "";
                if (isAlnumBoundary(previous)) {
                    output.append(" ");
                }
                output.append(marker).append(inner).append(marker);
                int afterClose = closeIndex + marker.length();
                String nextChar = afterClose < length ? String.valueOf(text.charAt(afterClose)) : "";
                if (isAlnumBoundary(nextChar)) {
                    output.append(" ");
                }
            }

            index = closeIndex + marker.length();
        }

        return output.toString();
    }
}
