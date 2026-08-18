from __future__ import annotations

import re

from ._emphasis import normalize_emphasis_boundaries
from ._lists import normalize_list_line
from ._protect import protect_many_and_restore
from ._tables import expand_compact_table_line, remove_incomplete_tables

_BR_TAG_RE = re.compile(r"<br\s*/?\s*>", re.IGNORECASE)
_CODE_FENCE_LABELS = {"", "markdown", "md", "json"}


def _strip_wrapping_code_fence(text: str) -> str:
    """LLMs sometimes wrap an already-markdown answer in a ```markdown
    fence, as if it were a code sample instead of the answer itself."""
    if not text.startswith("```"):
        return text

    first_newline = text.find("\n")
    if first_newline == -1:
        return text

    fence_label = text[3:first_newline].strip().lower()
    if fence_label not in _CODE_FENCE_LABELS:
        return text

    text = text[first_newline + 1 :]
    if text.rstrip().endswith("```"):
        text = text.rstrip()[:-3]
    return text


def _convert_br_tags_outside_tables(line: str) -> str:
    stripped_left = line.lstrip(" \t")
    if stripped_left.startswith("|"):
        line = stripped_left
        # <br> inside a table cell is intentional (multi-line cell content) —
        # converting it to a real newline would break the table structure.
        return line
    if "<br" in line.lower():
        line = _BR_TAG_RE.sub("\n", line)
    return line


def _clean_lines(text: str) -> str:
    # normalize_list_line() must run exactly once per output line. It used to
    # also run once before expand_compact_table_line() as well as once after,
    # which was silently safe under the old (buggy) indent-scale formula
    # because that formula happened to be a fixed point at 4 spaces — but
    # became a real bug (indentation doubling on every pass) once the scale
    # was fixed to be consistently 2-spaces-per-level. See issue #1.
    #
    # Lines inside an *embedded* code fence (one that isn't the whole-document
    # wrapper already stripped above) are passed through completely untouched
    # and marked as protected, so remove_incomplete_tables() below never mistakes
    # e.g. a `| not | a | table |` code comment for a real table. Without this,
    # code samples containing list markers or pipe characters were being
    # silently corrupted -- see issue #2.
    cleaned_lines: list[str] = []
    protected_indices: set[int] = set()
    in_code_fence = False

    for line in text.splitlines():
        is_fence_marker = line.strip().startswith("```")

        if in_code_fence:
            protected_indices.add(len(cleaned_lines))
            cleaned_lines.append(line)
            if is_fence_marker:
                in_code_fence = False
            continue

        if is_fence_marker:
            in_code_fence = True
            cleaned_lines.append(line)
            continue

        line = _convert_br_tags_outside_tables(line)
        line = normalize_emphasis_boundaries(line)
        for expanded_line in expand_compact_table_line(line):
            normalized = normalize_list_line(expanded_line, cleaned_lines[-1] if cleaned_lines else "")
            cleaned_lines.append(normalized)

    cleaned_lines = remove_incomplete_tables(cleaned_lines, frozenset(protected_indices))
    return "\n".join(cleaned_lines).strip()


def clean_markdown(text: object, *, protect_patterns: list[re.Pattern[str]] | None = None) -> str:
    """Fix markdown that LLMs commonly generate incorrectly.

    Handles, in a single left-to-right pass (no whole-string backtracking,
    so this stays fast even on long documents):

    - a stray ```markdown fence wrapped around the whole answer
    - `<br>` tags outside of tables (converted to real newlines)
    - `**bold**text` glued directly onto surrounding words
    - inconsistent list indentation
    - tables collapsed onto a single line
    - tables missing a separator row / with mismatched column counts
      (dropped instead of rendered broken)

    Args:
        text: the raw LLM output. Also accepts a list of
            ``{"text": ...}``-shaped chunks (as some streaming/multi-part
            LLM SDKs return), which are concatenated first.
        protect_patterns: optional list of compiled regexes. Any text
            matching one of these is left completely untouched by the
            cleanup passes above — use this to protect your own
            domain-specific syntax (custom tokens, template placeholders,
            etc.) that might otherwise get mangled.

    Returns:
        The cleaned markdown string. Empty/falsy input returns "".
    """
    if not text:
        return ""

    if isinstance(text, list):
        text = "".join(
            str(item.get("text", item)) if isinstance(item, dict) else str(item)
            for item in text
        )
    elif not isinstance(text, str):
        text = str(text)

    text = _strip_wrapping_code_fence(text.strip())

    if protect_patterns:
        return protect_many_and_restore(text, protect_patterns, _clean_lines)
    return _clean_lines(text)
