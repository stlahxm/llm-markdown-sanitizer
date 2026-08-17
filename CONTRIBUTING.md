# Contributing

Thanks for considering a contribution. This is a small, single-purpose library, so the bar is intentionally low-friction:

## Before you start

- For a bug fix or small improvement, just open a PR directly — no need to file an issue first.
- For anything that changes the public API (`clean_markdown`'s signature, return type, or default behavior), please open an issue first so we can agree on the approach before you write code.
- Check open issues/PRs first to avoid duplicate work.

## Project principles

- **Zero runtime dependencies.** This library only uses the Python standard library (`re`). Please don't add a dependency without discussing it in an issue first — it's a hard constraint, not a preference.
- **Pure functions.** No I/O, no global state, no side effects. `clean_markdown(text) -> str` and its internal helpers should stay easy to reason about and test in isolation.
- Keep diffs focused — one fix/feature per PR.

## Setup

```bash
git clone https://github.com/<you>/llm-markdown-sanitizer
cd llm-markdown-sanitizer
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
pytest -v
```

## Submitting a PR

- [ ] Add a test that fails before your change and passes after it (see `tests/test_core.py` for the style — small, focused, one behavior per test).
- [ ] `pytest -v` passes locally.
- [ ] Briefly describe the markdown input that was breaking, and why, in the PR description.

That's it — no DCO/CLA sign-off required. By opening a PR you agree your contribution is licensed under this project's MIT license.

## Reporting a bug

Include: the input string that produces the wrong output, what you expected, and what you got instead. A minimal repro is worth more than a long description.
