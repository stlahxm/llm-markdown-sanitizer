package io.github.stlahxm.markdownsanitizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repairs GFM markdown tables that LLMs frequently mangle:
 *
 * <ul>
 *   <li>compact tables where every row got glued onto one line</li>
 *   <li>"tables" missing a separator row, with mismatched column counts, or
 *       only a header (i.e. not really tables) -- these get dropped rather
 *       than rendered broken</li>
 * </ul>
 */
final class TableFixer {

    private static final Pattern COMPACT_TABLE_BOUNDARY = Pattern.compile("\\|\\s+\\|(?=\\s*(?::?-{3,}:?|[^|\\s]))");
    private static final Pattern ALIGNMENT_ROW = Pattern.compile("\\|\\s*:?-{3,}:?\\s*\\|");
    private static final Pattern COMPACT_ROW_BOUNDARY = Pattern.compile("\\|\\s+\\|");
    private static final Pattern SEPARATOR_CELL = Pattern.compile("^:?-{3,}:?$");

    private TableFixer() {
    }

    /**
     * Splits a table row on {@code |} characters, the same way GFM itself
     * does: a pipe does NOT start a new cell if it's backslash-escaped
     * ({@code \|}) or if it falls inside a backtick-delimited inline code
     * span ({@code `a|b`}) -- both are valid ways to put a literal
     * {@code |} inside a cell. A naive split on {@code "|"} treats every
     * pipe as a column boundary, which mismatches the row's cell count
     * against the header/separator row and gets the whole (valid!) table
     * dropped as "broken".
     */
    private static List<String> splitTableCells(String text) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inCodeSpan = false;
        int i = 0;
        int length = text.length();

        while (i < length) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < length && text.charAt(i + 1) == '|') {
                current.append("\\|");
                i += 2;
                continue;
            }
            if (c == '`') {
                inCodeSpan = !inCodeSpan;
                current.append(c);
                i++;
                continue;
            }
            if (c == '|' && !inCodeSpan) {
                cells.add(current.toString());
                current.setLength(0);
                i++;
                continue;
            }
            current.append(c);
            i++;
        }

        cells.add(current.toString());
        return cells;
    }

    /**
     * Restores a GFM table that was collapsed onto a single line back into
     * one line per row.
     */
    static List<String> expandCompactTableLine(String line) {
        String stripped = line.strip();
        if (!stripped.startsWith("|")) {
            return List.of(line);
        }

        boolean hasAlignmentRow = ALIGNMENT_ROW.matcher(stripped).find();
        boolean hasCompactRowBoundary = COMPACT_ROW_BOUNDARY.matcher(stripped).find();
        if (!hasAlignmentRow || !hasCompactRowBoundary) {
            return List.of(line);
        }

        Matcher matcher = COMPACT_TABLE_BOUNDARY.matcher(stripped);
        String expanded = matcher.replaceAll("|\n|");
        return List.of(expanded.split("\n", -1));
    }

    /**
     * Number of {@code |}-delimited cells in a table row. A row that doesn't
     * start with {@code |} isn't a table row at all, so it counts as 0 --
     * this lets callers use the count directly as a truthiness/comparison
     * check without a separate "is this even a table row" branch.
     */
    private static int cellCount(String line) {
        String stripped = line.strip();
        if (!stripped.startsWith("|")) {
            return 0;
        }
        if (stripped.endsWith("|")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return splitTableCells(stripped).size() - 1;
    }

    /**
     * A GFM separator row is the {@code | --- | --- |}-style line under the
     * header that declares column alignment -- every cell must be dashes
     * (optionally with a leading/trailing {@code :} for alignment), nothing else.
     */
    private static boolean isSeparatorRow(String line) {
        String stripped = line.strip();
        stripped = trimPipes(stripped).strip();
        if (stripped.isEmpty()) {
            return false;
        }
        List<String> cells = splitTableCells(stripped);
        for (String cell : cells) {
            if (!SEPARATOR_CELL.matcher(cell.strip()).matches()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTableLine(List<String> lines, int index, java.util.Set<Integer> protectedIndices) {
        return !protectedIndices.contains(index) && lines.get(index).stripLeading().startsWith("|");
    }

    private static String trimPipes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '|') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '|') {
            end--;
        }
        return value.substring(start, end);
    }

    /**
     * @param protectedIndices line indices that came from inside a code fence
     *                         (verbatim content) -- these are never grouped into
     *                         a table block even if they start with {@code |},
     *                         since they aren't a table at all and must not be
     *                         altered or dropped.
     */
    static List<String> removeIncompleteTables(List<String> lines, java.util.Set<Integer> protectedIndices) {
        List<String> output = new ArrayList<>();
        int index = 0;

        while (index < lines.size()) {
            if (!isTableLine(lines, index, protectedIndices)) {
                output.add(lines.get(index));
                index++;
                continue;
            }

            int blockStart = index;
            List<String> tableBlock = new ArrayList<>();
            while (index < lines.size() && isTableLine(lines, index, protectedIndices)) {
                tableBlock.add(lines.get(index));
                index++;
            }

            boolean hasSeparator = tableBlock.stream().anyMatch(TableFixer::isSeparatorRow);
            List<String> dataRows = tableBlock.stream().filter(l -> !isSeparatorRow(l)).toList();
            int expectedColumns = tableBlock.isEmpty() ? 0 : cellCount(tableBlock.get(0));
            boolean columnCountsMatch = expectedColumns > 1
                    && tableBlock.stream().allMatch(l -> cellCount(l) == expectedColumns);
            boolean hasDataRow = dataRows.size() >= 2;

            if (hasSeparator && hasDataRow && columnCountsMatch) {
                output.addAll(tableBlock);
            } else {
                // The block gets dropped. Two bits of cosmetic cleanup
                // around the hole it leaves behind:
                String previous = output.isEmpty() ? "" : output.get(output.size() - 1).strip();
                String nextLine = index < lines.size() ? lines.get(index).strip() : "";
                if (previous.endsWith(":") && (nextLine.isEmpty() || nextLine.startsWith("#"))) {
                    // e.g. "Here's a table:" immediately introducing the
                    // dropped block, with nothing (or a new heading) after
                    // it -- that dangling intro sentence reads as broken on
                    // its own, so drop it too instead of leaving an
                    // orphaned colon-ended line.
                    output.remove(output.size() - 1);
                } else if (blockStart > 0 && !output.isEmpty() && !output.get(output.size() - 1).isEmpty()) {
                    // Otherwise just leave a blank line where the table
                    // was, so surrounding paragraphs don't get glued
                    // together.
                    output.add("");
                }
            }
        }

        return output;
    }
}
