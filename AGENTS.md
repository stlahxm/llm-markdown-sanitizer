# Agent instructions

Applies to any AI coding agent working in this repo (Claude Code, Codex, Cursor, Amp, Jules, etc.). Same rules as [CONTRIBUTING.md](CONTRIBUTING.md), phrased for an agent rather than a human contributor.

## Repository layout

This is a monorepo with two language bindings that are meant to behave identically:

- `python/` — the PyPI package (`llm-markdown-sanitizer`)
- `java/` — the JitPack-distributed Java library (`io.github.stlahxm.markdownsanitizer`)

Each side has one module per concern, same split on both languages:

| Concern | Python | Java |
|---|---|---|
| Orchestration / entry point | `_core.py` | `MarkdownSanitizer.java` |
| Placeholder protect/restore primitive | `_protect.py` | `PlaceholderProtector.java` |
| Bold/italic emphasis spacing | `_emphasis.py` | `EmphasisFixer.java` |
| List markers/indentation | `_lists.py` | `ListFixer.java` |
| Table repair | `_tables.py` | `TableFixer.java` |
| Blank-line/heading-space spacing fixes | `_spacing.py` | `SpacingFixer.java` |
| Smart-quote normalization in code | `_quotes.py` | `QuoteFixer.java` |

A behavior change made in one should generally be ported to the other. When fixing a bug, check whether the equivalent code exists in both `python/src/llm_markdown_sanitizer/` and `java/src/main/java/io/github/stlahxm/markdownsanitizer/` before considering the fix complete.

## Project scope

A single-purpose library: fix markdown that LLMs commonly generate incorrectly (entry points: `clean_markdown()` in Python, `MarkdownSanitizer.clean()` in Java). If a change doesn't relate to that, it likely doesn't belong here — avoid expanding scope.

## Constraints (both languages)

1. **Zero runtime dependencies.** Standard library only (Python: `re`, `itertools`; Java: `java.util.regex`, `java.util`). Do not add a dependency without prior discussion.
2. **Pure functions/methods.** No I/O, no global mutable state, no side effects.
3. **One public entry point per language.** Internal modules/classes (Python's `_`-prefixed files, Java's package-private classes) should not gain new public exports without discussion.

## A bug class to watch for

Both ports share a placeholder-based protect/restore mechanism (Python: `_protect.py`; Java: `PlaceholderProtector.java`) that gives every call its own numeric namespace deliberately. An earlier version used a single shared namespace, and a nested call (emphasis normalization protecting math spans, running inside an outer caller-supplied protect call) collided with and silently deleted the outer call's protected text — no crash, just dropped content. If this file is modified in either language, run the full test suite before and after, and check whether the same issue needs fixing on the other side.

Similarly, in the list-normalization code (Python: `_lists.py`; Java: `ListFixer.java`): the line-normalization function must run exactly once per line. It previously ran twice in the orchestration code (once before table expansion, once after) without visible effect, because the old indent-scale formula happened to be a fixed point. That is no longer true after the formula was corrected to a consistent 2-spaces-per-level scale (per CommonMark §5.2) — reintroducing a duplicate call would double indentation on every pass.

## Before submitting a change

Python:
```bash
cd python && pip install -e ".[dev]" && pytest -v
```

Java:
```bash
cd java && ./gradlew build
```

- Add a test that fails before the change and passes after it.
- If the fix originates from a real LLM output that broke, use an anonymized version of that input as the test case rather than a synthetic one — the test suites are meant to encode observed failure modes.
- Note in the PR/commit whether the equivalent fix is still needed in the other language.
- **File an issue for every bug fix, no exceptions** — including ones found and fixed in the same sitting during a review, with no external report. If the fix is already done by the time the issue is filed, file it anyway and close it referencing the fix commit. Don't fold a bug's discovery/root-cause/fix into a commit message alone; the issue is the record that a human (or a future agent) can find later.
