"""Normalize markdown list markers and indentation.

LLMs are inconsistent about list indentation (2 spaces vs 4, tabs vs
spaces, odd mixed levels). This collapses everything onto a consistent
4-space-per-level `-` marker convention, which renders reliably across
markdown engines (react-markdown, remark, GitHub's renderer, etc).
"""

from __future__ import annotations

import re

_LIST_MARKER_RE = re.compile(r"^([ \t]*)([-*+])\s+(.+)$")
_INDENTED_TEXT_RE = re.compile(r"^[ \t]{4,}\S")

MAX_INDENT_LEVEL = 3


def normalize_list_line(line: str, previous_line: str) -> str:
    """Normalize a single line's list marker/indentation.

    `previous_line` is used to decide whether a heavily-indented plain-text
    line is actually a continuation of the previous list item.
    """
    stripped = line.lstrip(" \t")
    if not stripped or stripped.startswith("|") or stripped.startswith("```"):
        return line

    list_match = _LIST_MARKER_RE.match(line)
    if list_match:
        indent_width = len(list_match.group(1).replace("\t", "    "))
        indent_level = min(
            indent_width // 4 if indent_width % 4 == 0 else max(1, indent_width // 2),
            MAX_INDENT_LEVEL,
        )
        indent = "    " * indent_level
        return f"{indent}- {list_match.group(3).strip()}"

    if _INDENTED_TEXT_RE.match(line) and re.match(r"^\s{0,2}-\s+", previous_line):
        return f"    - {stripped}"

    return line
