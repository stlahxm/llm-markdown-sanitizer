"""Structural spacing fixes: a missing space after `#` in an ATX heading,
and a missing blank line before a list/heading that immediately follows a
paragraph -- both cause CommonMark-strict renderers to treat the line as
plain paragraph text instead of the structural element it's meant to be.
Reported as one of the most common causes of "broken" LLM markdown output
in community troubleshooting threads (missing blank line) and general
markdown guides (missing heading space).
"""

from __future__ import annotations

import re

# Only triggers when a letter (Latin or Korean) immediately follows the `#`
# run -- not a digit or symbol -- to avoid misreading something like
# "#1 priority" or a hashtag-style "#tag" as an intended heading that's
# just missing its space.
_HEADING_NO_SPACE_RE = re.compile(r"^([ \t]{0,3}#{1,6})([A-Za-z가-힣].*)$")
_HEADING_RE = re.compile(r"^[ \t]{0,3}#{1,6}(?:\s|$)")
_LIST_START_RE = re.compile(r"^[ \t]*(?:[-*+]|\d+[.)])\s+")


def fix_heading_missing_space(line: str) -> str:
    """`#Heading` -> `# Heading`."""
    match = _HEADING_NO_SPACE_RE.match(line)
    if not match:
        return line
    return f"{match.group(1)} {match.group(2)}"


def is_heading(line: str) -> bool:
    return bool(_HEADING_RE.match(line))


def is_list_start(line: str) -> bool:
    return bool(_LIST_START_RE.match(line))


def needs_blank_line_before(line: str, previous_line: str) -> bool:
    """A list or heading immediately following a non-blank paragraph line
    (one that isn't itself a list item, a heading, or a table row) needs a
    blank line inserted before it -- otherwise CommonMark-strict renderers
    treat it as a continuation of that paragraph rather than a new
    structural element, and it renders as plain text."""
    if not (is_heading(line) or is_list_start(line)):
        return False
    if not previous_line or not previous_line.strip():
        return False
    if is_heading(previous_line) or is_list_start(previous_line):
        return False
    if previous_line.lstrip().startswith("|"):
        return False
    return True
