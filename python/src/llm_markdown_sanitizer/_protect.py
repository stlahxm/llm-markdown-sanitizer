"""Generic protect-transform-restore primitive.

Some regex-based cleanup passes are destructive to spans that happen to look
like the pattern being fixed (e.g. a `**bold**` normalizer mangling `**`
that appears inside a LaTeX math span like `$a^{**}$`). This module lets a
transform temporarily swap out matched spans for an opaque placeholder,
run its cleanup, then restore the original text untouched.

It's also exposed publicly (`clean_markdown(text, protect_patterns=[...])`)
so callers can protect their own domain-specific tokens (custom wiki syntax,
special annotations, etc.) from being touched by the built-in cleanup passes.
"""

from __future__ import annotations

import itertools
import re
from collections.abc import Callable

_PLACEHOLDER_PREFIX = "MDSAN"
# Each call gets its own numeric namespace so that nested protect_and_restore
# calls (e.g. emphasis normalization protecting math spans, while already
# running inside an outer protect_patterns() call) can never mistake one
# another's placeholders for their own and wipe them out during restore.
_call_counter = itertools.count()


def protect_and_restore(
    text: str,
    pattern: re.Pattern[str],
    transform: Callable[[str], str],
) -> str:
    """Protect all matches of `pattern` in `text`, run `transform` on the
    rest, then restore the protected spans verbatim."""
    call_id = next(_call_counter)
    # The trailing "@@" terminator is required, not decorative: without an
    # unambiguous non-digit boundary, a placeholder immediately followed by
    # a literal digit in the source text (e.g. protecting "$5...$10" leaves
    # "...MDSAN0_010" once the transform runs) makes the restore regex's
    # `\d+` greedily swallow that trailing digit into the index, producing
    # an out-of-range index and leaking the raw placeholder into the output.
    # Found via clean_markdown("costs $5 and $10, so**buy**it") leaking
    # "MDSAN0_010" verbatim into the result.
    placeholder_re = re.compile(re.escape(f"@@{_PLACEHOLDER_PREFIX}{call_id}_") + r"(\d+)@@")
    segments: list[str] = []

    def _replace(match: re.Match[str]) -> str:
        token = f"@@{_PLACEHOLDER_PREFIX}{call_id}_{len(segments)}@@"
        segments.append(match.group(0))
        return token

    protected = pattern.sub(_replace, text)
    transformed = transform(protected)

    def _restore(match: re.Match[str]) -> str:
        index = int(match.group(1))
        return segments[index] if 0 <= index < len(segments) else match.group(0)

    return placeholder_re.sub(_restore, transformed)


def protect_many_and_restore(
    text: str,
    patterns: list[re.Pattern[str]],
    transform: Callable[[str], str],
) -> str:
    """Same as `protect_and_restore` but for a list of patterns, applied in order."""
    if not patterns:
        return transform(text)

    def _chain(remaining: list[re.Pattern[str]], value: str) -> str:
        if not remaining:
            return transform(value)
        return protect_and_restore(value, remaining[0], lambda v: _chain(remaining[1:], v))

    return _chain(patterns, text)
