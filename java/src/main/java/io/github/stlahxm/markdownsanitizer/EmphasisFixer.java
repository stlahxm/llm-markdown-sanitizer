package io.github.stlahxm.markdownsanitizer;

import java.util.regex.Pattern;

/**
 * Fixes {@code **bold**} spans that LLMs glue directly onto surrounding
 * words, e.g. {@code **Note**this breaks rendering} -&gt;
 * {@code **Note** this breaks rendering}.
 *
 * <p>Protects inline math spans ({@code $...$} / {@code $$...$$}) from
 * being touched, since {@code **} can legitimately appear inside LaTeX
 * there.
 */
final class EmphasisFixer {

    private static final Pattern MATH_SPAN = Pattern.compile("\\$\\$?[\\s\\S]*?\\$\\$?");

    private EmphasisFixer() {
    }

    static String normalizeBoundaries(String line) {
        return PlaceholderProtector.protectAndRestore(line, MATH_SPAN, EmphasisFixer::normalizeTokens);
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
     * Single left-to-right scan: copy characters through unchanged until a
     * {@code **} is found, then look for its matching close on the rest of
     * the (already math-span-protected) string. A complete {@code **...**}
     * pair gets re-emitted with a boundary space added on whichever
     * side(s) would otherwise glue onto adjacent alphanumeric text. An
     * unmatched {@code **} is copied through literally rather than dropped
     * (see the comment below).
     */
    private static String normalizeTokens(String text) {
        StringBuilder output = new StringBuilder();
        int index = 0;
        int length = text.length();

        while (index < length) {
            if (!text.startsWith("**", index)) {
                output.appendCodePoint(text.codePointAt(index));
                index += Character.charCount(text.codePointAt(index));
                continue;
            }

            int closeIndex = text.indexOf("**", index + 2);
            if (closeIndex == -1) {
                // No matching close on this line -- this isn't emphasis at all
                // (could be Java varargs-adjacent syntax, Python's `**kwargs`
                // in an embedded code sample, or just a stray typo). Keep the
                // literal characters instead of silently dropping them; only
                // complete `**...**` pairs get normalized.
                output.append("**");
                index += 2;
                continue;
            }

            String inner = text.substring(index + 2, closeIndex).strip();
            if (!inner.isEmpty()) {
                String previous = output.length() > 0 ? String.valueOf(output.charAt(output.length() - 1)) : "";
                if (isAlnumBoundary(previous)) {
                    output.append(" ");
                }
                output.append("**").append(inner).append("**");
                String nextChar = closeIndex + 2 < length ? String.valueOf(text.charAt(closeIndex + 2)) : "";
                if (isAlnumBoundary(nextChar)) {
                    output.append(" ");
                }
            }

            index = closeIndex + 2;
        }

        return output.toString();
    }
}
