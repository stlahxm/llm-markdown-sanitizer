"""Normalizes 'smart'/typographic quotes to their straight ASCII
equivalents, but only inside code (fenced blocks and inline `code` spans).
LLMs trained on prose habitually emit curly quotes even inside code, which
then fails to parse/compile or looks wrong when copy-pasted. Prose text
outside code is left alone, since curly quotes there may be intentional
stylistic output rather than a bug -- this is deliberately scoped to code,
not a blanket typographic-quote "fix".
"""

from __future__ import annotations

import re

_QUOTE_MAP = str.maketrans(
    {
        "“": '"',  # left double quotation mark
        "”": '"',  # right double quotation mark
        "‘": "'",  # left single quotation mark
        "’": "'",  # right single quotation mark
    }
)

_INLINE_CODE_SPAN_RE = re.compile(r"`[^`\n]+`")


def normalize_quotes_in_fenced_line(line: str) -> str:
    """For a line already known (by the caller) to be inside a fenced
    code block -- the whole line is code, so the whole line is normalized."""
    return line.translate(_QUOTE_MAP)


def normalize_quotes_in_inline_code(line: str) -> str:
    """Normalizes smart quotes only inside inline `code` spans on an
    otherwise-normal line, leaving surrounding prose untouched."""

    def _replace(match: re.Match[str]) -> str:
        return match.group(0).translate(_QUOTE_MAP)

    return _INLINE_CODE_SPAN_RE.sub(_replace, line)
