"""Repair GFM markdown tables that LLMs frequently mangle:

- compact tables where every row got glued onto one line
- "tables" that are missing a separator row, have mismatched column
  counts, or only have a header (i.e. aren't really tables) — these get
  dropped rather than rendered as broken tables.
"""

from __future__ import annotations

import re

_COMPACT_TABLE_BOUNDARY_RE = re.compile(r"\s*\|\s+\|(?=\s*(?::?-{3,}:?|[^|\s]))")
_TABLE_SEPARATOR_CELL_RE = re.compile(r"^:?-{3,}:?$")


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
    stripped = line.strip()
    if not stripped.startswith("|"):
        return 0
    if stripped.endswith("|"):
        stripped = stripped[:-1]
    return len(stripped.split("|")) - 1


def _is_separator_row(line: str) -> bool:
    stripped = line.strip().strip("|").strip()
    if not stripped:
        return False
    cells = [cell.strip() for cell in stripped.split("|")]
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
            previous = output[-1].strip() if output else ""
            next_line = lines[index].strip() if index < len(lines) else ""
            if previous.endswith(":") and (not next_line or next_line.startswith("#")):
                output.pop()
            elif block_start > 0 and output and output[-1] != "":
                output.append("")

    return output
