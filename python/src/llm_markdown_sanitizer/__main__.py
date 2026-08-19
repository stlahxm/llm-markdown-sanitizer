"""CLI entry point: `python -m llm_markdown_sanitizer <file> [<file> ...]`.

Rewrites each file in place with `clean_markdown()` applied, printing the
paths that changed. Exists so this package can be wired into external
tooling (pre-commit hooks, editor integrations) that expects a runnable
command rather than a library import -- the public API is still just
`clean_markdown()`; this is a thin wrapper around it, not a second API.
"""

from __future__ import annotations

import sys

from ._core import clean_markdown


def main(argv: list[str] | None = None) -> int:
    paths = argv if argv is not None else sys.argv[1:]
    if not paths:
        print("usage: python -m llm_markdown_sanitizer <file> [<file> ...]", file=sys.stderr)
        return 2

    changed = False
    for path in paths:
        with open(path, encoding="utf-8") as f:
            original = f.read()
        cleaned = clean_markdown(original)
        if cleaned != original:
            with open(path, "w", encoding="utf-8") as f:
                f.write(cleaned)
            print(f"fixed: {path}")
            changed = True
    return 1 if changed else 0


if __name__ == "__main__":
    raise SystemExit(main())
