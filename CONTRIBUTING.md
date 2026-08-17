# Contributing

This is a small, single-purpose library, so I'm trying to keep the bar low here:

## Before you dive in

- Bug fix or small improvement? Just open a PR, no need to file an issue first.
- Changing the public API (`clean_markdown`'s signature, return type, or default behavior)? Open an issue first so we can talk it through before you write code — I'd rather agree on the approach up front than ask you to redo a PR.
- Worth a quick search of open issues/PRs first, in case someone's already on it.

## The two rules that actually matter

1. **Zero runtime dependencies.** Stdlib only. If you're tempted to reach for a package to solve something, that's usually a sign the problem needs a smaller solution, not a bigger one — ping me in an issue if you genuinely think an exception is warranted.
2. **Pure functions.** No I/O, no global state, no side effects. Everything here should be testable by calling it with a string and checking what comes back.

Beyond that — keep PRs focused on one thing. A drive-by reformat mixed into a bug fix makes the diff annoying to review.

## Setup

```bash
git clone https://github.com/<you>/llm-markdown-sanitizer
cd llm-markdown-sanitizer
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
pytest -v
```

## Submitting a PR

- Add a test that fails before your change and passes after (look at `tests/` for the style — small, one behavior per test, real input/output).
- `pytest -v` passes.
- Say what markdown input was breaking and why, in the PR description. A concrete "here's the input, here's what it produced, here's what I expected" beats a long explanation.

No DCO/CLA sign-off — opening a PR here means you're fine with it being under this project's MIT license, same as everything else in the repo.

## Reporting a bug

Give me the exact input string, what you expected, and what you actually got. A minimal repro saves both of us time.
