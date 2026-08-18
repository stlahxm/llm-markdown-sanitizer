"""Fix `**bold**` (and `***bold italic***`) spans that LLMs glue directly
onto surrounding words, e.g. `**Note**this breaks rendering` ->
`**Note** this breaks rendering`.

Also protects inline math spans (`$...$` / `$$...$$`) from being touched,
since `**`/`***` can legitimately appear inside LaTeX there.
"""

from __future__ import annotations

import re

from ._protect import protect_and_restore

_MATH_SPAN_RE = re.compile(r"\${1,2}[\s\S]*?\${1,2}")
# `$...$` isn't part of the CommonMark/GFM spec -- it's a convention some
# renderers interpret as LaTeX math, with no authoritative grammar to tell
# "real" math apart from two unrelated dollar amounts on the same line
# (e.g. "costs $5 and $10" would otherwise be treated as one math span
# spanning both). Real math essentially never contains two bare, plain-
# language words separated by nothing but whitespace (LaTeX commands are
# `\text{...}`-wrapped or separated by operators/braces, not bare spaces),
# so two or more such words in a row is a strong signal the candidate is
# actually prose with incidental dollar signs, not math -- declined rather
# than protected. This is a heuristic, not a parser -- it won't be right
# for every input, but it's strictly better than treating every "$" pair
# as math.
_PROSE_LIKE_RE = re.compile(r"[A-Za-z가-힣]{2,}\s+[A-Za-z가-힣]{2,}")


def _looks_like_math(span: str) -> bool:
    inner = span.strip("$")
    return not _PROSE_LIKE_RE.search(inner)


def _is_alnum_boundary(value: str) -> bool:
    """Whether `value` (a single character, or "" for "no character here")
    would visually/parseably glue onto a `**` marker if left touching it.
    `str.isalnum()` is Unicode-aware, so this treats Korean, CJK, and
    accented Latin characters the same as plain ASCII letters/digits with
    no per-language special-casing -- and correctly leaves emoji, symbols,
    and punctuation alone, since those don't have the same gluing problem."""
    return bool(value) and value.isalnum()


def _marker_at(text: str, index: int) -> str | None:
    """Which emphasis marker (if any) starts at `index`. `***` is checked
    before `**` since it's the longer marker and `**` is a prefix of it --
    checking in the other order would always match the `**` case first and
    never recognize a triple asterisk."""
    if text.startswith("***", index):
        return "***"
    if text.startswith("**", index):
        return "**"
    return None


def _normalize_emphasis_tokens(text: str) -> str:
    """Single left-to-right scan: copy characters through unchanged until a
    `**` or `***` marker is found, then look for a matching *same-width*
    close on the rest of the (already math-span-protected) string. A
    complete pair gets re-emitted with a boundary space added on whichever
    side(s) would otherwise glue onto adjacent alphanumeric text. An
    unmatched marker is copied through literally (see the comment below)
    rather than dropped."""
    output: list[str] = []
    index = 0

    while index < len(text):
        marker = _marker_at(text, index)
        if marker is None:
            output.append(text[index])
            index += 1
            continue

        close_index = text.find(marker, index + len(marker))
        if close_index == -1:
            # No matching close on this line -- this isn't emphasis at all
            # (could be Python's `**kwargs`, a dict-unpacking `**`, or just a
            # stray typo). Keep the literal characters instead of silently
            # dropping them; only complete marker...marker pairs of the
            # same width get normalized.
            output.append(marker)
            index += len(marker)
            continue

        inner = text[index + len(marker) : close_index].strip()
        if inner:
            previous = output[-1] if output else ""
            if _is_alnum_boundary(previous):
                output.append(" ")
            output.append(f"{marker}{inner}{marker}")
            next_char = text[close_index + len(marker)] if close_index + len(marker) < len(text) else ""
            if _is_alnum_boundary(next_char):
                output.append(" ")

        index = close_index + len(marker)

    return "".join(output)


def normalize_emphasis_boundaries(line: str) -> str:
    def transform(input_text: str) -> str:
        return _normalize_emphasis_tokens(input_text)

    real_math_spans = [m.group(0) for m in _MATH_SPAN_RE.finditer(line) if _looks_like_math(m.group(0))]
    if not real_math_spans:
        return transform(line)

    # Protect only the specific spans that passed the math-likeness check
    # above, not every "$...$" candidate -- built as a precise alternation
    # of this line's actual accepted spans rather than re-running the
    # broad, greedy _MATH_SPAN_RE against protect_and_restore directly.
    only_real_math = re.compile("|".join(re.escape(span) for span in real_math_spans))
    return protect_and_restore(line, only_real_math, transform)
