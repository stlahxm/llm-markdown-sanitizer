package io.github.stlahxm.markdownsanitizer;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Fixes markdown that LLMs commonly generate incorrectly.
 *
 * <p>Handles, in a single left-to-right pass (no whole-string backtracking,
 * so this stays fast even on long documents):
 *
 * <ul>
 *   <li>a stray {@code ```markdown} fence wrapped around the whole answer</li>
 *   <li>{@code <br>} tags outside of tables (converted to real newlines)</li>
 *   <li>{@code **bold**text} glued directly onto surrounding words</li>
 *   <li>inconsistent list indentation</li>
 *   <li>tables collapsed onto a single line</li>
 *   <li>tables missing a separator row / with mismatched column counts
 *       (dropped instead of rendered broken)</li>
 * </ul>
 *
 * <p>Java port of the Python <a href="https://github.com/stlahxm/llm-markdown-sanitizer">
 * llm-markdown-sanitizer</a> package -- same behavior, same test fixtures,
 * ported after that package's bug fixes (see {@link ListFixer} and
 * {@link PlaceholderProtector} for the two bugs found and fixed there before
 * this port existed).
 */
public final class MarkdownSanitizer {

    private static final Pattern BR_TAG = Pattern.compile("<br\\s*/?\\s*>", Pattern.CASE_INSENSITIVE);
    private static final List<String> CODE_FENCE_LABELS = List.of("", "markdown", "md", "json");

    private MarkdownSanitizer() {
    }

    /**
     * Equivalent to {@link #clean(String, List)} with no protected patterns.
     *
     * @param text the raw LLM output. {@code null} or empty returns {@code ""}.
     * @return the cleaned markdown
     */
    public static String clean(String text) {
        return clean(text, List.of());
    }

    /**
     * @param text            the raw LLM output. {@code null} or empty returns {@code ""}.
     * @param protectPatterns text matching any of these patterns is left completely
     *                        untouched by the cleanup passes -- use this to protect
     *                        your own domain-specific syntax (custom tokens, template
     *                        placeholders, etc.) that might otherwise get mangled.
     * @return the cleaned markdown
     */
    public static String clean(String text, List<Pattern> protectPatterns) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String stripped = stripWrappingCodeFence(text.strip());

        if (protectPatterns.isEmpty()) {
            return cleanLines(stripped);
        }
        return PlaceholderProtector.protectManyAndRestore(stripped, protectPatterns, MarkdownSanitizer::cleanLines);
    }

    private static String stripWrappingCodeFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }

        int firstNewline = text.indexOf('\n');
        if (firstNewline == -1) {
            return text;
        }

        String fenceLabel = text.substring(3, firstNewline).strip().toLowerCase();
        if (!CODE_FENCE_LABELS.contains(fenceLabel)) {
            return text;
        }

        String rest = text.substring(firstNewline + 1);
        String trimmedEnd = rest.stripTrailing();
        if (trimmedEnd.endsWith("```")) {
            return trimmedEnd.substring(0, trimmedEnd.length() - 3);
        }
        return rest;
    }

    private static String convertBrTagsOutsideTables(String line) {
        String strippedLeft = line.stripLeading();
        if (strippedLeft.startsWith("|")) {
            // <br> inside a table cell is usually intentional (multi-line
            // cell content) -- converting it to a real newline would break
            // the table structure.
            return strippedLeft;
        }
        if (line.toLowerCase().contains("<br")) {
            return BR_TAG.matcher(line).replaceAll("\n");
        }
        return line;
    }

    private static String cleanLines(String text) {
        // normalizeLine() must run exactly once per output line. An earlier
        // version of the pipeline (and its Python sibling) called it twice --
        // once before table-expansion, once after -- which was harmless
        // under a buggy indent-scale formula that happened to be a fixed
        // point, but would double indentation on every pass under the
        // corrected formula used here. Only call it once, after expansion.
        //
        // Lines inside an *embedded* code fence (one that isn't the whole-
        // document wrapper already stripped above) are passed through
        // completely untouched and marked as protected, so
        // removeIncompleteTables() below never mistakes e.g. a
        // `| not | a | table |` code comment for a real table. Without this,
        // code samples containing list markers or pipe characters were being
        // silently corrupted.
        List<String> cleanedLines = new java.util.ArrayList<>();
        java.util.Set<Integer> protectedIndices = new java.util.HashSet<>();
        boolean inCodeFence = false;

        for (String rawLine : text.split("\n", -1)) {
            boolean isFenceMarker = rawLine.strip().startsWith("```");

            if (inCodeFence) {
                protectedIndices.add(cleanedLines.size());
                // The whole line is code, but "smart" quotes inside it
                // aren't protected content in the same sense as everything
                // else here -- they're a separate, common LLM mistake
                // (curly quotes break code that gets copy-pasted or
                // parsed) worth fixing even inside an otherwise-untouched
                // fence.
                cleanedLines.add(QuoteFixer.normalizeQuotesInFencedLine(rawLine));
                if (isFenceMarker) {
                    inCodeFence = false;
                }
                continue;
            }

            if (isFenceMarker) {
                inCodeFence = true;
                cleanedLines.add(rawLine);
                continue;
            }

            String line = convertBrTagsOutsideTables(rawLine);
            line = SpacingFixer.fixHeadingMissingSpace(line);
            line = QuoteFixer.normalizeQuotesInInlineCode(line);
            line = EmphasisFixer.normalizeBoundaries(line);
            for (String expandedLine : TableFixer.expandCompactTableLine(line)) {
                String previous = cleanedLines.isEmpty() ? "" : cleanedLines.get(cleanedLines.size() - 1);
                String normalized = ListFixer.normalizeLine(expandedLine, previous);
                if (SpacingFixer.needsBlankLineBefore(normalized, previous)) {
                    cleanedLines.add("");
                }
                cleanedLines.add(normalized);
            }
        }

        if (inCodeFence) {
            // The model never closed its own fence -- a common failure
            // mode, especially when the answer gets cut off or the model
            // loses track of an inner illustrative fence nested inside the
            // real one. Close it rather than leaving the rest of the
            // document (if any) rendered as literal code, or losing the
            // closing marker's absence silently.
            cleanedLines.add("```");
        }

        cleanedLines = TableFixer.removeIncompleteTables(cleanedLines, protectedIndices);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < cleanedLines.size(); i++) {
            if (i > 0) {
                result.append('\n');
            }
            result.append(cleanedLines.get(i));
        }
        return result.toString().strip();
    }
}
