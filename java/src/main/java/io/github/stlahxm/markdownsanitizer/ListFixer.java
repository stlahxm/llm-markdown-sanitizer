package io.github.stlahxm.markdownsanitizer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes markdown list markers and indentation.
 *
 * <p>LLMs are inconsistent about list indentation (2 spaces vs 4, tabs vs
 * spaces, odd mixed levels). This collapses everything onto a consistent
 * 4-space-per-level {@code -} marker convention, which renders reliably
 * across markdown engines.
 *
 * <p>Why 2 raw input spaces = one nesting level: per the CommonMark spec
 * (<a href="https://spec.commonmark.org/0.31.2/#list-items">5.2 List
 * items, "Basic case"</a>), a list item's contents are considered nested
 * under it once indented by W + N spaces, where W is the marker's width
 * and N is 1-4 spaces of required indentation after the marker. For a
 * {@code -} marker (W=1) that's a minimum of 1 + 1 = 2 spaces.
 */
final class ListFixer {

    private static final Pattern LIST_MARKER = Pattern.compile("^([ \\t]*)([-*+])\\s+(.+)$");
    private static final Pattern INDENTED_TEXT = Pattern.compile("^[ \\t]{4,}\\S");
    private static final Pattern PREVIOUS_LINE_IS_LIST_ITEM = Pattern.compile("^\\s{0,2}-\\s+");

    private static final int MAX_INDENT_LEVEL = 3;
    private static final int SPACES_PER_LEVEL = 2;

    private ListFixer() {
    }

    static String normalizeLine(String line, String previousLine) {
        String stripped = stripLeading(line);
        if (stripped.isEmpty() || stripped.startsWith("|") || stripped.startsWith("```")) {
            return line;
        }

        Matcher listMatch = LIST_MARKER.matcher(line);
        if (listMatch.matches()) {
            int indentWidth = listMatch.group(1).replace("\t", "    ").length();
            int indentLevel = indentWidth > 0 ? Math.max(1, indentWidth / SPACES_PER_LEVEL) : 0;
            indentLevel = Math.min(indentLevel, MAX_INDENT_LEVEL);
            return "    ".repeat(indentLevel) + "- " + listMatch.group(3).strip();
        }

        if (INDENTED_TEXT.matcher(line).find() && PREVIOUS_LINE_IS_LIST_ITEM.matcher(previousLine).find()) {
            return "    - " + stripped;
        }

        return line;
    }

    private static String stripLeading(String line) {
        int index = 0;
        while (index < line.length() && (line.charAt(index) == ' ' || line.charAt(index) == '\t')) {
            index++;
        }
        return line.substring(index);
    }
}
