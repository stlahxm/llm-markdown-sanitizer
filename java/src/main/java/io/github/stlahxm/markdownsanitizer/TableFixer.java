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

    private static final Pattern COMPACT_TABLE_BOUNDARY = Pattern.compile("\\s*\\|\\s+\\|(?=\\s*(?::?-{3,}:?|[^|\\s]))");
    private static final Pattern ALIGNMENT_ROW = Pattern.compile("\\|\\s*:?-{3,}:?\\s*\\|");
    private static final Pattern COMPACT_ROW_BOUNDARY = Pattern.compile("\\|\\s+\\|");
    private static final Pattern SEPARATOR_CELL = Pattern.compile("^:?-{3,}:?$");

    private TableFixer() {
    }

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

    private static int cellCount(String line) {
        String stripped = line.strip();
        if (!stripped.startsWith("|")) {
            return 0;
        }
        if (stripped.endsWith("|")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped.split("\\|", -1).length - 1;
    }

    private static boolean isSeparatorRow(String line) {
        String stripped = line.strip();
        stripped = trimPipes(stripped).strip();
        if (stripped.isEmpty()) {
            return false;
        }
        String[] cells = stripped.split("\\|", -1);
        for (String cell : cells) {
            if (!SEPARATOR_CELL.matcher(cell.strip()).matches()) {
                return false;
            }
        }
        return true;
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

    static List<String> removeIncompleteTables(List<String> lines) {
        List<String> output = new ArrayList<>();
        int index = 0;

        while (index < lines.size()) {
            if (!lines.get(index).stripLeading().startsWith("|")) {
                output.add(lines.get(index));
                index++;
                continue;
            }

            int blockStart = index;
            List<String> tableBlock = new ArrayList<>();
            while (index < lines.size() && lines.get(index).stripLeading().startsWith("|")) {
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
                String previous = output.isEmpty() ? "" : output.get(output.size() - 1).strip();
                String nextLine = index < lines.size() ? lines.get(index).strip() : "";
                if (previous.endsWith(":") && (nextLine.isEmpty() || nextLine.startsWith("#"))) {
                    output.remove(output.size() - 1);
                } else if (blockStart > 0 && !output.isEmpty() && !output.get(output.size() - 1).isEmpty()) {
                    output.add("");
                }
            }
        }

        return output;
    }
}
