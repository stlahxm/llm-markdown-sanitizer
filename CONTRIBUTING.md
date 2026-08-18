# Contributing

This is a small, single-purpose library, so the process here is intentionally lightweight.

## Repository layout

This is a monorepo with two independent language bindings sharing the same behavior:

- `python/` — the PyPI package
- `java/` — the JitPack-distributed Java library

A bug fix or behavior change should generally be ported to both, since they're meant to behave identically. It's fine to open two separate PRs (one per language) rather than one combined PR.

## Before you start

- Every bug fix needs a corresponding issue, even a self-found one fixed in the same sitting. File it before or alongside the PR — a short title, the input that broke, expected vs. actual output, and root cause once known. If the fix is already done, file the issue and close it referencing the fix; the issue is the paper trail, not a gate to wait on.
- For anything that changes the public API's signature, return type, or default behavior, open an issue first so the approach can be agreed on before implementation.
- Check open issues/PRs first to avoid duplicate work.

## Project constraints (both languages)

1. **Zero runtime dependencies.** Standard library only. Please don't introduce a dependency to solve a problem — if you think one is genuinely needed, raise it in an issue first.
2. **Pure functions/methods.** No I/O, no global state, no side effects. Everything should be testable with a string in, string out.

Beyond that, keep PRs focused on a single change — mixing a reformat into a bug fix makes the diff harder to review.

## Setup

Python:

```bash
cd python
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
pytest -v
```

Java:

```bash
cd java
./gradlew build
```

## Submitting a PR

- Add a test that fails before your change and passes after it (see the existing tests in `python/tests/` or `java/src/test/` for style).
- Confirm the relevant test suite passes locally.
- Describe the markdown input that was breaking and why, in the PR description.
- If the fix applies to both languages, note in the PR whether the other language's port still needs updating (a follow-up PR/issue is fine).

No DCO/CLA sign-off is required. By opening a PR, you agree your contribution is licensed under this project's MIT license.

## Reporting a bug

Include the input string, the expected output, and the actual output, plus which language binding it's about. A minimal reproduction is more useful than a long description.

## Releases

Python and Java are versioned and tagged independently, since they don't necessarily release in lockstep:

- Python: tag `python-vX.Y.Z` triggers a PyPI publish.
- Java: tag `java-vX.Y.Z` — no publish step needed, JitPack builds directly from the tag on first request.
