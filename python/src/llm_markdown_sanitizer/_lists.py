"""Normalize markdown list markers and indentation.

LLMs are inconsistent about list indentation (2 spaces vs 4, tabs vs
spaces, odd mixed levels). This collapses everything onto a consistent
4-space-per-level `-` marker convention, which renders reliably across
markdown engines (react-markdown, remark, GitHub's renderer, etc).

Why 2 raw input spaces = one nesting level: per the CommonMark spec
(https://spec.commonmark.org/0.31.2/#list-items, "Basic case"), a list
item's contents are considered nested under it once indented by
W + N spaces, where W is the marker's width and N is 1-4 spaces of
required indentation after the marker. For a `-` marker (W=1) that's a
minimum of 1 + 1 = 2 spaces. That's also what LLMs converge on in
practice when they emit nested `-` lists, since they're trained on
real-world markdown that follows this rule. Earlier versions of this
normalizer switched between a "//4" and a "//2" scale depending on
whether the raw indent happened to be a multiple of 4, which silently
collapsed 2-space and 4-space nesting levels into the same output level
(see tests/test_integration.py and issue #1) — fixed by using a single
2-space-per-level scale unconditionally.
"""

from __future__ import annotations

import re

_LIST_MARKER_RE = re.compile(r"^([ \t]*)([-*+])\s+(.+)$")
_INDENTED_TEXT_RE = re.compile(r"^[ \t]{4,}\S")

MAX_INDENT_LEVEL = 3
_SPACES_PER_LEVEL = 2  # CommonMark minimum nesting indent for a `-` marker


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
        indent_level = max(1, indent_width // _SPACES_PER_LEVEL) if indent_width > 0 else 0
        indent_level = min(indent_level, MAX_INDENT_LEVEL)
        indent = "    " * indent_level
        return f"{indent}- {list_match.group(3).strip()}"

    if _INDENTED_TEXT_RE.match(line) and re.match(r"^\s{0,2}-\s+", previous_line):
        return f"    - {stripped}"

    return line
