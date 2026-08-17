# Agent instructions

This file applies to any AI coding agent (Claude Code, Codex, Cursor, Amp, Jules, etc.) working in this repo. See [CONTRIBUTING.md](CONTRIBUTING.md) for the human-facing version — this is the same rules, agent-oriented.

## What this project is

A single-purpose library: `clean_markdown(text) -> str` fixes broken markdown that LLMs commonly generate. Nothing else. Resist scope creep — if a fix doesn't relate to "an LLM produced malformed markdown," it probably doesn't belong here.

## Hard constraints — do not violate these without being asked

1. **Zero runtime dependencies.** Stdlib only (`re`, `itertools`). Do not add a dependency to solve a problem — solve it with what's already here.
2. **Pure functions only.** No I/O, no global mutable state, no side effects. Every function should be testable by calling it with a string and checking the return value.
3. **No public API surface beyond `clean_markdown`.** The `_`-prefixed modules (`_core.py`, `_tables.py`, `_lists.py`, `_emphasis.py`, `_protect.py`) are internal — don't add new public exports without discussion.

## A specific bug class to watch for

The placeholder-based protect/restore mechanism (`_protect.py`) gives every call its own numeric namespace (`MDSAN{call_id}_{n}`) specifically because an earlier version used a single shared namespace (`MDSAN_{n}`) and nested calls (emphasis normalization protecting math spans, running inside an outer user-supplied `protect_patterns` call) collided and silently deleted each other's protected text. If you touch `_protect.py`, re-run the full test suite — this bug class is easy to reintroduce and easy to miss (it doesn't crash, it just silently drops text).

## Before submitting a change

```bash
pip install -e ".[dev]"
pytest -v
```

- Add a test in `tests/test_core.py` that fails before your change and passes after — see existing tests for the style (one behavior per test, real input/output pairs, no mocking needed since everything is pure functions).
- If your fix came from a real LLM output that broke, prefer using an anonymized version of that actual input as the test case over a synthetic one — this project's test suite is meant to encode real observed failure modes, not just designed-in edge cases.
