# Agent instructions

Applies to any AI coding agent working in this repo (Claude Code, Codex, Cursor, Amp, Jules, etc.). Same rules as [CONTRIBUTING.md](CONTRIBUTING.md), phrased for an agent rather than a human contributor.

## Project scope

A single-purpose library: `clean_markdown(text) -> str` fixes markdown that LLMs commonly generate incorrectly. If a change doesn't relate to that, it likely doesn't belong here — avoid expanding scope.

## Constraints

1. **Zero runtime dependencies.** Standard library only (`re`, `itertools`). Do not add a dependency without prior discussion.
2. **Pure functions.** No I/O, no global mutable state, no side effects. Every function should be testable with a string in, string out.
3. **`clean_markdown` is the only public export.** The `_`-prefixed modules are internal; do not add new public exports without discussion.

## A bug class to watch for

`_protect.py`'s placeholder mechanism gives every call its own numeric namespace (`MDSAN{call_id}_{n}`) deliberately. An earlier version used a single shared namespace, and a nested call (emphasis normalization protecting math spans, running inside an outer `protect_patterns` call) collided with and silently deleted the outer call's protected text — no crash, just dropped content. If `_protect.py` is modified, run the full test suite before and after.

Similarly, in `_lists.py`: `normalize_list_line()` must run exactly once per line. It previously ran twice inside `_clean_lines()` (once before table expansion, once after) without visible effect, because the old indent-scale formula happened to be a fixed point. That is no longer true after the formula was corrected — reintroducing a duplicate call would double indentation on every pass.

## Before submitting a change

```bash
pip install -e ".[dev]"
pytest -v
```

- Add a test that fails before the change and passes after it.
- If the fix originates from a real LLM output that broke, use an anonymized version of that input as the test case rather than a synthetic one — the test suite is meant to encode observed failure modes.
