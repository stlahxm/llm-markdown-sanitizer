# llm-markdown-sanitizer

[![Python CI](https://img.shields.io/github/actions/workflow/status/stlahxm/llm-markdown-sanitizer/python-ci.yml?branch=main&label=python)](.github/workflows/python-ci.yml)
[![Java CI](https://img.shields.io/github/actions/workflow/status/stlahxm/llm-markdown-sanitizer/java-ci.yml?branch=main&label=java)](.github/workflows/java-ci.yml)
[![PyPI](https://img.shields.io/pypi/v/llm-markdown-sanitizer?label=pypi)](https://pypi.org/project/llm-markdown-sanitizer/)
[![JitPack](https://img.shields.io/github/v/tag/stlahxm/llm-markdown-sanitizer?filter=java-v*&label=jitpack)](https://jitpack.io/#stlahxm/llm-markdown-sanitizer)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
![Zero dependencies](https://img.shields.io/badge/dependencies-zero-brightgreen)
![Python 3.9+](https://img.shields.io/badge/python-3.9%2B-blue)
![Java 17+](https://img.shields.io/badge/java-17%2B-orange)

Fix broken markdown that LLMs generate — tables, lists, emphasis, code fences. Zero dependencies, one function/method.

- **Two language bindings, one behavior** — Python and Java share the same logic and the same test fixtures.
- **Unicode-aware by construction** — the emphasis-spacing fix relies on `isalnum()`/`isLetterOrDigit()`, so Korean, CJK, and accented Latin text (café, 한글, 中文) are handled the same as plain ASCII with no per-language code path. Emoji and symbols are correctly left alone.
- **No configuration** — call one function, get cleaned markdown back.

**Contents:** [Before/after](#before--after) · [Python install](python/#install) · [Java install](java/#install) · [Supported syntax](#supported-syntax-and-exact-behavior) · [Contributing](#contributing)

## Before / after

An LLM response like this — code fence wrapper, bold glued to text, a stray `<br>`, a table collapsed onto one line, a second table missing its separator row, more glued bold:

````
```markdown
# Release Notes

**Breaking**this version changes the auth flow.<br>Update your client before upgrading.

## Changes

- Auth
  - New token format
    - Backward compatible for 30 days
- Rate limiting

| Endpoint | Change | | --- | --- | | /login | New response shape | | /refresh | Deprecated |

Known issues (missing separator row):
| Issue | Status |
| Memory leak | Fixed |

See the**migration guide**for details.
```
````

...becomes this, in one call:

```
# Release Notes

**Breaking** this version changes the auth flow.
Update your client before upgrading.

## Changes

- Auth
    - New token format
        - Backward compatible for 30 days
- Rate limiting

| Endpoint | Change|
| --- | ---|
| /login | New response shape|
| /refresh | Deprecated |


See the **migration guide** for details.
```

The broken second table (no separator row) is dropped entirely rather than rendered as a wall of `|`. Everything else is repaired in place, with no configuration required.

Extracted from the markdown-cleanup layer of a production RAG service, after months of hardening against real LLM output. Available for both Python and Java, sharing the same behavior and test fixtures.

- **[Python](python/)** — `pip install llm-markdown-sanitizer`
- **[Java](java/)** — via JitPack (`com.github.stlahxm:llm-markdown-sanitizer:java-<version>`)

Each subdirectory has its own README with install/usage details specific to that language. The core logic — code fence stripping, `<br>` handling, emphasis boundary fixes, list indentation, table repair — is ported 1:1 between the two, including bug fixes (see [CONTRIBUTING.md](CONTRIBUTING.md) for how changes should be kept in sync across both).

## Supported syntax and exact behavior

Everything below applies identically to the Python and Java bindings. Processing happens in a single left-to-right pass, line by line — this matters for a few of the boundary cases noted below.

### Wrapping code fence

- Only strips a fence that wraps the **entire** input (starts at position 0, after trimming whitespace). Code fences appearing anywhere else in the document — i.e. an actual code sample the model included on purpose — are left alone.
- Only strips it when the fence's language label is empty, `markdown`, `md`, or `json` (case-insensitive). A ` ```python ` or ` ```java ` fence is real code and is never touched.

### `<br>` tags

- Matches `<br>`, `<br/>`, `<br />` (case-insensitive, any spacing before the slash).
- Converted to a real newline **only outside table rows**. A line is treated as a table row if it starts with `|` after stripping leading whitespace — inside those, `<br>` is left as literal text, since GFM tables use it intentionally for multi-line cell content.

### `**bold**` spacing

- Detects `**...**` spans and inserts a space on either side if the adjacent character would otherwise be glued to it.
- "Glued" is decided by Unicode alphanumeric classification (`Character.isLetterOrDigit` in Java / `str.isalnum()` in Python) — this covers Latin letters, digits, Korean Hangul, CJK characters, and accented letters (café, naïve, etc.) uniformly, with no per-language special-casing needed. Punctuation, symbols, whitespace, and emoji are **not** alphanumeric, so `**bold**😀` or `**bold**©` are left as-is (they don't have the rendering ambiguity `**bold**word` has in some markdown engines).
- `**` inside inline math spans (`$...$` or `$$...$$`) is never touched, so LaTeX like `$a^{**}b$` survives untouched even though it contains the exact `**` sequence being fixed elsewhere.
- Empty bold (`****`) is dropped rather than re-emitted.
- Unclosed `**` (no matching closing pair) is left as plain text — not treated as an error, just skipped.
- Only recognized within a single line — a `**bold**` span split across a line break by the model is not detected or fixed.

### List indentation

- Recognizes `-`, `*`, or `+` bullet markers; all are normalized to `-` in the output.
- Indentation scale is a flat 2 raw spaces (tabs expanded to 4 spaces) per nesting level, capped at 3 levels deep — this is the CommonMark-minimum nesting indent for a `-` marker (marker width 1 + 1 required space), and also what LLMs converge on in practice. See [`_lists.py`](python/src/llm_markdown_sanitizer/_lists.py) / [`ListFixer.java`](java/src/main/java/io/github/stlahxm/markdownsanitizer/ListFixer.java) for the exact derivation.
- A heavily indented (4+ spaces) plain-text line immediately following a near-top-level list item is treated as a wrapped continuation of that item and re-indented under it.
- **Not handled**: ordered lists (`1.`, `2.`, ...) are passed through untouched — this library only normalizes bullet lists.
- Lines inside a table or a code fence are never touched by list normalization.

### Table repair

A block of consecutive `|`-prefixed lines is checked against three conditions before being kept:

1. at least one row is a valid GFM separator row (every cell matches `:?-{3,}:?`, e.g. `---` or `:--:`)
2. at least 2 non-separator (data) rows exist
3. every row in the block has the same cell count, **and** that count is greater than 1 — single-column "tables" are always dropped, since a lone `| text |` line is usually just a stray pipe character rather than an intentional table

Blocks that fail any of these are dropped entirely (along with a dangling `Some intro text:` line immediately before them, if present) rather than left to render as a wall of `|` characters.

Separately, a table collapsed onto a single line (every row's `|`-boundaries glued together with just whitespace between them) is expanded back into one row per line **before** the validity check above runs.

### Protecting your own syntax (`protectPatterns` / `protect_patterns`)

- Caller-supplied regex patterns are matched and fully protected from every pass above, applied in the order given, then restored verbatim.
- Uses an internal placeholder mechanism where each protection call — including the library's own internal math-span protection — gets an isolated namespace, so a caller's custom pattern can never collide with (and be silently erased by) the library's own internal bookkeeping. This was a real bug found and fixed during development; see the git history for `_protect.py` / `PlaceholderProtector.java`.

### Input handling

- `None` / empty input returns `""`.
- A list of `{"text": ...}`-shaped chunks (the shape some streaming LLM SDKs return) is concatenated before processing (Python only, via a plain `list` argument — the Java port takes a `String` directly).

### Explicitly out of scope

Headings, blockquotes, horizontal rules, inline code spans, links, and images are passed through untouched — they weren't observed as a source of broken LLM output in the production system this library was extracted from, so there was nothing to fix. If you hit a real-world broken-markdown case not covered above, please [open an issue](https://github.com/stlahxm/llm-markdown-sanitizer/issues) with the input that triggered it.

## Contributing

Bug fixes and small improvements are welcome. No CLA/DCO required — see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines. AI coding agents should pick up [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) automatically.

## License

MIT
