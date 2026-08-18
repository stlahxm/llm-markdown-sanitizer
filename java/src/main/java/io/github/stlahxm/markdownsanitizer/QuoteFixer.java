package io.github.stlahxm.markdownsanitizer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes "smart"/typographic quotes to their straight ASCII
 * equivalents, but only inside code (fenced blocks and inline {@code code}
 * spans). LLMs trained on prose habitually emit curly quotes even inside
 * code, which then fails to parse/compile or looks wrong when copy-pasted.
 * Prose text outside code is left alone, since curly quotes there may be
 * intentional stylistic output rather than a bug -- this is deliberately
 * scoped to code, not a blanket typographic-quote "fix".
 */
final class QuoteFixer {

    private static final Pattern INLINE_CODE_SPAN = Pattern.compile("`[^`\\n]+`");

    private QuoteFixer() {
    }

    private static String translateQuotes(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '“': // left double quotation mark
                case '”': // right double quotation mark
                    result.append('"');
                    break;
                case '‘': // left single quotation mark
                case '’': // right single quotation mark
                    result.append('\'');
                    break;
                default:
                    result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * For a line already known (by the caller) to be inside a fenced code
     * block -- the whole line is code, so the whole line is normalized.
     */
    static String normalizeQuotesInFencedLine(String line) {
        return translateQuotes(line);
    }

    /**
     * Normalizes smart quotes only inside inline {@code code} spans on an
     * otherwise-normal line, leaving surrounding prose untouched.
     */
    static String normalizeQuotesInInlineCode(String line) {
        Matcher matcher = INLINE_CODE_SPAN.matcher(line);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(line, lastEnd, matcher.start());
            result.append(translateQuotes(matcher.group()));
            lastEnd = matcher.end();
        }
        result.append(line, lastEnd, line.length());
        return result.toString();
    }
}
