# Agent instructions

For any AI coding agent working in this repo (Claude Code, Codex, Cursor, Amp, Jules, whatever). Same rules as [CONTRIBUTING.md](CONTRIBUTING.md), just phrased for you instead of a human.

## What you're looking at

A single-purpose library: `clean_markdown(text) -> str` fixes markdown that LLMs tend to mangle. That's the whole project. If a change doesn't relate to "an LLM produced broken markdown," it probably doesn't belong here — resist the urge to grow scope.

## Don't break these without asking first

1. **Zero runtime dependencies.** Stdlib only (`re`, `itertools`). Don't reach for a package — figure it out with what's already here.
2. **Pure functions.** No I/O, no global mutable state, no side effects. Everything should be testable with a plain string in, string out.
3. **`clean_markdown` is the only public thing.** The `_`-prefixed modules are internal. Don't add new public exports on your own.

## A bug that's easy to reintroduce

`_protect.py`'s placeholder mechanism gives every call its own numeric namespace (`MDSAN{call_id}_{n}`) on purpose. An earlier version shared one namespace across all calls, and a nested call (emphasis normalization protecting math spans, running inside an outer user-supplied `protect_patterns` call) ended up deleting the outer call's protected text because it thought the placeholder was its own. It didn't crash — it just silently dropped text, which is the worst kind of bug to catch. If you touch `_protect.py`, run the full suite before and after.

Similar story with `_lists.py`: `normalize_list_line()` must run exactly once per line. It used to run twice (once before table-expansion, once after) and got away with it because the old indent-scale math happened to be a fixed point — running it twice didn't change the output. That's no longer true. If you're touching the list/table interaction in `_core.py`, be careful not to reintroduce a double call.

## Before you're done

```bash
pip install -e ".[dev]"
pytest -v
```

- Add a test that fails before your change, passes after. Look at existing tests for the style.
- If the fix came from a real LLM output that broke, use an anonymized version of that actual input as the test case instead of making one up — the test suite here is meant to encode real observed failures, not just hypothetical edge cases.
