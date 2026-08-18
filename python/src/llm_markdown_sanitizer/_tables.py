"""Repair GFM markdown tables that LLMs frequently mangle:

- compact tables where every row got glued onto one line
- "tables" that are missing a separator row, have mismatched column
  counts, or only have a header (i.e. aren't really tables) — these get
  dropped rather than rendered as broken tables.
"""

from __future__ import annotations

import re

_COMPACT_TABLE_BOUNDARY_RE = re.compile(r"\|\s+\|(?=\s*(?::?-{3,}:?|[^|\s]))")
_TABLE_SEPARATOR_CELL_RE = re.compile(r"^:?-{3,}:?$")


def _split_table_cells(text: str) -> list[str]:
    """Split a table row on `|` characters, the same way GFM itself does:
    a pipe does NOT start a new cell if it's backslash-escaped (`\\|`) or
    if it falls inside a backtick-delimited inline code span (`` `a|b` ``)
    -- both are valid ways to put a literal `|` inside a cell. A naive
    `str.split("|")` treats every pipe as a column boundary, which
    mismatches the row's cell count against the header/separator row and
    gets the whole (valid!) table dropped as "broken"."""
    cells: list[str] = []
    current: list[str] = []
    in_code_span = False
    i = 0
    length = len(text)

    while i < length:
        char = text[i]
        if char == "\\" and i + 1 < length and text[i + 1] == "|":
            current.append("\\|")
            i += 2
            continue
        if char == "`":
            in_code_span = not in_code_span
            current.append(char)
            i += 1
            continue
        if char == "|" and not in_code_span:
            cells.append("".join(current))
            current = []
            i += 1
            continue
        current.append(char)
        i += 1

    cells.append("".join(current))
    return cells


def expand_compact_table_line(line: str) -> list[str]:
    """Restore a GFM table that was collapsed onto a single line back into
    one line per row."""
    stripped = line.strip()
    if not stripped.startswith("|"):
        return [line]

    has_alignment_row = bool(re.search(r"\|\s*:?-{3,}:?\s*\|", stripped))
    has_compact_row_boundary = bool(re.search(r"\|\s+\|", stripped))
    if not has_alignment_row or not has_compact_row_boundary:
        return [line]

    expanded = _COMPACT_TABLE_BOUNDARY_RE.sub("|\n|", stripped)
    return expanded.splitlines()


def _cell_count(line: str) -> int:
    """Number of `|`-delimited cells in a table row. A row that doesn't
    start with `|` isn't a table row at all, so it counts as 0 -- this lets
    callers use the count directly as a truthiness/comparison check without
    a separate "is this even a table row" branch."""
    stripped = line.strip()
    if not stripped.startswith("|"):
        return 0
    if stripped.endswith("|"):
        stripped = stripped[:-1]
    return len(_split_table_cells(stripped)) - 1


def _is_separator_row(line: str) -> bool:
    """A GFM separator row is the `| --- | --- |`-style line under the
    header that declares column alignment -- every cell must be dashes
    (optionally with a leading/trailing `:` for alignment), nothing else."""
    stripped = line.strip().strip("|").strip()
    if not stripped:
        return False
    cells = [cell.strip() for cell in _split_table_cells(stripped)]
    return bool(cells) and all(_TABLE_SEPARATOR_CELL_RE.match(cell) for cell in cells)


def remove_incomplete_tables(lines: list[str], protected_indices: frozenset[int] = frozenset()) -> list[str]:
    """Drop table-like blocks that don't actually form a valid GFM table
    (missing separator row, mismatched column counts, or fewer than 2 data
    rows) instead of letting them render as broken markdown.

    `protected_indices` marks line indices that came from inside a code
    fence (verbatim content) -- those are never grouped into a table block
    even if they happen to start with `|`, since they aren't a table at all
    and must not be altered or dropped.
    """
    output: list[str] = []
    index = 0

    def _is_table_line(i: int) -> bool:
        return i not in protected_indices and lines[i].lstrip().startswith("|")

    while index < len(lines):
        if not _is_table_line(index):
            output.append(lines[index])
            index += 1
            continue

        block_start = index
        table_block: list[str] = []
        while index < len(lines) and _is_table_line(index):
            table_block.append(lines[index])
            index += 1

        has_separator = any(_is_separator_row(line) for line in table_block)
        data_rows = [line for line in table_block if not _is_separator_row(line)]
        expected_columns = _cell_count(table_block[0]) if table_block else 0
        column_counts_match = expected_columns > 1 and all(
            _cell_count(line) == expected_columns for line in table_block
        )
        has_data_row = len(data_rows) >= 2

        if has_separator and has_data_row and column_counts_match:
            output.extend(table_block)
        else:
            # The block gets dropped. Two bits of cosmetic cleanup around
            # the hole it leaves behind:
            previous = output[-1].strip() if output else ""
            next_line = lines[index].strip() if index < len(lines) else ""
            if previous.endswith(":") and (not next_line or next_line.startswith("#")):
                # e.g. "Here's a table:" immediately introducing the dropped
                # block, with nothing (or a new heading) after it -- that
                # dangling intro sentence reads as broken on its own, so
                # drop it too instead of leaving an orphaned colon-ended line.
                output.pop()
            elif block_start > 0 and output and output[-1] != "":
                # Otherwise just leave a blank line where the table was,
                # so surrounding paragraphs don't get glued together.
                output.append("")

    return output
