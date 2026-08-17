# Contributing

This is a small, single-purpose library, so the process here is intentionally lightweight.

## Before you start

- For a bug fix or small improvement, open a PR directly — no need to file an issue first.
- For anything that changes the public API (`clean_markdown`'s signature, return type, or default behavior), please open an issue first so the approach can be agreed on before implementation.
- Check open issues/PRs first to avoid duplicate work.

## Project constraints

1. **Zero runtime dependencies.** Standard library only. Please don't introduce a dependency to solve a problem — if you think one is genuinely needed, raise it in an issue first.
2. **Pure functions.** No I/O, no global state, no side effects. Every function should be testable with a string in, string out.

Beyond that, keep PRs focused on a single change — mixing a reformat into a bug fix makes the diff harder to review.

## Setup

```bash
git clone https://github.com/<you>/llm-markdown-sanitizer
cd llm-markdown-sanitizer
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
pytest -v
```

## Submitting a PR

- Add a test that fails before your change and passes after it (see `tests/` for the existing style).
- Confirm `pytest -v` passes locally.
- Describe the markdown input that was breaking and why, in the PR description.

No DCO/CLA sign-off is required. By opening a PR, you agree your contribution is licensed under this project's MIT license.

## Reporting a bug

Include the input string, the expected output, and the actual output. A minimal reproduction is more useful than a long description.
