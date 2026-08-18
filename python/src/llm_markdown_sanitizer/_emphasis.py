"""Fix `**bold**` spans that LLMs glue directly onto surrounding words,
e.g. `**Note**this breaks rendering` -> `**Note** this breaks rendering`.

Also protects inline math spans (`$...$` / `$$...$$`) from being touched,
since `**` can legitimately appear inside LaTeX there.
"""

from __future__ import annotations

import re

from ._protect import protect_and_restore

_MATH_SPAN_RE = re.compile(r"\${1,2}[\s\S]*?\${1,2}")


def _is_alnum_boundary(value: str) -> bool:
    return bool(value) and value.isalnum()


def _normalize_emphasis_tokens(text: str) -> str:
    output: list[str] = []
    index = 0

    while index < len(text):
        if not text.startswith("**", index):
            output.append(text[index])
            index += 1
            continue

        close_index = text.find("**", index + 2)
        if close_index == -1:
            # No matching close on this line -- this isn't emphasis at all
            # (could be Python's `**kwargs`, a dict-unpacking `**`, or just a
            # stray typo). Keep the literal characters instead of silently
            # dropping them; only complete `**...**` pairs get normalized.
            output.append("**")
            index += 2
            continue

        inner = text[index + 2 : close_index].strip()
        if inner:
            previous = output[-1] if output else ""
            if _is_alnum_boundary(previous):
                output.append(" ")
            output.append(f"**{inner}**")
            next_char = text[close_index + 2] if close_index + 2 < len(text) else ""
            if _is_alnum_boundary(next_char):
                output.append(" ")

        index = close_index + 2

    return "".join(output)


def normalize_emphasis_boundaries(line: str) -> str:
    def transform(input_text: str) -> str:
        return _normalize_emphasis_tokens(input_text)

    return protect_and_restore(line, _MATH_SPAN_RE, transform)
