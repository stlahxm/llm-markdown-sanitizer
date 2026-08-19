# llm-markdown-sanitizer

[![Python CI](https://img.shields.io/github/actions/workflow/status/stlahxm/llm-markdown-sanitizer/python-ci.yml?branch=main&label=python)](.github/workflows/python-ci.yml)
[![Java CI](https://img.shields.io/github/actions/workflow/status/stlahxm/llm-markdown-sanitizer/java-ci.yml?branch=main&label=java)](.github/workflows/java-ci.yml)
[![PyPI](https://img.shields.io/pypi/v/llm-markdown-sanitizer?label=pypi)](https://pypi.org/project/llm-markdown-sanitizer/)
[![JitPack](https://img.shields.io/github/v/tag/stlahxm/llm-markdown-sanitizer?filter=java-v*&label=jitpack)](https://jitpack.io/#stlahxm/llm-markdown-sanitizer)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
![Zero dependencies](https://img.shields.io/badge/dependencies-zero-brightgreen)
![Python 3.9+](https://img.shields.io/badge/python-3.9%2B-blue)
![Java 17+](https://img.shields.io/badge/java-17%2B-orange)

<img width="2162" height="1504" alt="PR" src="https://github.com/user-attachments/assets/581a599f-7b5a-44aa-9ec7-073c9411bc88" />


Fix broken markdown that LLMs generate — tables, lists, headings, emphasis, code fences, quotes. Zero dependencies, one function/method.

- **Two language bindings, one behavior** — Python and Java share the same logic and the same test fixtures.
- **Unicode-aware by construction** — the emphasis-spacing fix relies on `isalnum()`/`isLetterOrDigit()`, so Korean, CJK, and accented Latin text (café, 한글, 中文) are handled the same as plain ASCII with no per-language code path. Emoji and symbols are correctly left alone.
- **Backed by real bug reports, not guesswork** — every fix traces back either to a bug found while testing this library against realistic documents, or to a documented, commonly-reported LLM markdown failure from developer communities and public issue trackers (linked per-feature below).
- **No configuration** — call one function, get cleaned markdown back.

**Contents:** [Before/after](#before--after) · [Install & use](#install--use) · [Supported syntax](#supported-syntax-and-exact-behavior) · [Contributing](#contributing)

**How do I fix broken markdown from ChatGPT/LLM output in Python or Java?** Call `clean_markdown()` (Python) or `MarkdownSanitizer.clean()` (Java) on the raw LLM response before rendering or storing it — see [Install & use](#install--use) below.

## Before / after

An LLM response like this — code fence wrapper, bold glued to text, a stray `<br>`, a heading missing its space and its blank line, a table collapsed onto one line, a second table missing its separator row, more glued bold, curly quotes inside an inline code span:

````
```markdown
# Release Notes

**Breaking**this version changes the auth flow.<br>Update your client before upgrading.
##What changed
- Auth
  - New token format
    - Backward compatible for 30 days
- Rate limiting

| Endpoint | Change | | --- | --- | | /login | New response shape | | /refresh | Deprecated |

Known issues (missing separator row):
| Issue | Status |
| Memory leak | Fixed |

See the**migration guide**for details. Example: `git commit -m “fix bug”`
```
````

...becomes this, in one call:

```
# Release Notes

**Breaking** this version changes the auth flow.
Update your client before upgrading.

## What changed
- Auth
    - New token format
        - Backward compatible for 30 days
- Rate limiting

| Endpoint | Change |
| --- | --- |
| /login | New response shape |
| /refresh | Deprecated |


See the **migration guide** for details. Example: `git commit -m "fix bug"`
```

The broken second table (no separator row) is dropped entirely rather than rendered as a wall of `|`. Everything else is repaired in place, with no configuration required.

Extracted from the markdown-cleanup layer of a production RAG service, after months of hardening against real LLM output. Available for both Python and Java, sharing the same behavior and test fixtures.

**[Try it live in your browser](https://stlahxm.github.io/llm-markdown-sanitizer/)** — runs the real PyPI package via Pyodide, no install needed.

<img width="2162" height="1504" alt="PR" src="https://github.com/user-attachments/assets/581a599f-7b5a-44aa-9ec7-073c9411bc88" />

## Install & use

### Python

[![PyPI](https://img.shields.io/pypi/v/llm-markdown-sanitizer?label=pypi)](https://pypi.org/project/llm-markdown-sanitizer/)

```bash
pip install llm-markdown-sanitizer
```

```python
from llm_markdown_sanitizer import clean_markdown

clean_markdown("**Note**this needs a space")
# "**Note** this needs a space"
```

→ full install options (uv, pinning, requirements.txt) and more usage examples (FastAPI, streaming chunks) in **[python/README.md](python/)**. Package page: **https://pypi.org/project/llm-markdown-sanitizer/**

### Java

[![JitPack](https://jitpack.io/v/stlahxm/llm-markdown-sanitizer.svg)](https://jitpack.io/#stlahxm/llm-markdown-sanitizer)

`build.gradle.kts`:
```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
dependencies {
    implementation("com.github.stlahxm:llm-markdown-sanitizer:java-v0.2.2")
}
```

```java
import io.github.stlahxm.markdownsanitizer.MarkdownSanitizer;

MarkdownSanitizer.clean("**Note**this needs a space");
// "**Note** this needs a space"
```

→ Maven/Groovy DSL variants and a Spring Boot controller example in **[java/README.md](java/)**. Browse versions: **https://jitpack.io/#stlahxm/llm-markdown-sanitizer**

---

Each subdirectory has its own README with the full install/usage details for that language. The core logic — code fence stripping, `<br>` handling, emphasis boundary fixes, list indentation, table repair — is ported 1:1 between the two, including bug fixes (see [CONTRIBUTING.md](CONTRIBUTING.md) for how changes should be kept in sync across both).

## Supported syntax and exact behavior

Everything below applies identically to the Python and Java bindings. Processing happens in a single left-to-right pass, line by line — this matters for a few of the boundary cases noted below.

### Wrapping code fence

- Only strips a fence that wraps the **entire** input (starts at position 0, after trimming whitespace). Code fences appearing anywhere else in the document — i.e. an actual code sample the model included on purpose — are left alone.
- Only strips it when the fence's language label is empty, `markdown`, `md`, or `json` (case-insensitive). A ` ```python ` or ` ```java ` fence is real code and is never touched.

### Embedded code fences

Any ` ``` ` fence that isn't the whole-document wrapper (see above) opens a block whose content is passed through **completely untouched** — none of the passes below (bold spacing, list normalization, table repair, `<br>` conversion) run on it, and lines inside it are never mistaken for a table or list even if they start with `-` or `|`. This matters concretely: a Python code sample containing `**kwargs` or a `| not | a | table |` comment used to get corrupted (the `**` silently deleted, or the whole line dropped as a fake broken table) before this protection existed.

If the model never closes its own fence (a documented, recurring failure mode — see e.g. [anthropics/claude-code#17559](https://github.com/anthropics/claude-code/issues/17559)), a closing ` ``` ` is appended at the end of the document rather than leaving everything after it silently treated as more code (or the missing close silently ignored) — see [#13](https://github.com/stlahxm/llm-markdown-sanitizer/issues/13). This does not attempt to repair the harder, more ambiguous case of nested fences with mismatched lengths colliding mid-document — see [#9](https://github.com/stlahxm/llm-markdown-sanitizer/issues/9).

### `<br>` tags

- Matches `<br>`, `<br/>`, `<br />` (case-insensitive, any spacing before the slash).
- Converted to a real newline **only outside table rows**. A line is treated as a table row if it starts with `|` after stripping leading whitespace — inside those, `<br>` is left as literal text, since GFM tables use it intentionally for multi-line cell content.

### `**bold**` and `***bold italic***` spacing

- Detects `**...**` and `***...***` spans (matched as same-width pairs — a `***` marker is never closed by a `**`) and inserts a space on either side if the adjacent character would otherwise be glued to it.
- "Glued" is decided by Unicode alphanumeric classification (`Character.isLetterOrDigit` in Java / `str.isalnum()` in Python) — this covers Latin letters, digits, Korean Hangul, CJK characters, and accented letters (café, naïve, etc.) uniformly, with no per-language special-casing needed. Punctuation, symbols, whitespace, and emoji are **not** alphanumeric, so `**bold**😀` or `**bold**©` are left as-is (they don't have the rendering ambiguity `**bold**word` has in some markdown engines).
- `**`/`***` inside a *real* inline math span (`$...$` or `$$...$$`) is never touched, so LaTeX like `$a^{**}b$` survives untouched even though it contains the exact `**` sequence being fixed elsewhere. Math-span candidates are filtered by a heuristic before being protected — see "Known limitation" below.
- Empty bold (`****`) is dropped rather than re-emitted.
- Unclosed `**`/`***` (no matching closing pair of the same width) is left as plain text — not treated as an error, just skipped.
- Only recognized within a single line — a `**bold**` span split across a line break by the model is not detected or fixed.

### List indentation

- Recognizes `-`, `*`, or `+` bullet markers; **all are normalized to `-`** in the output, so a model switching marker characters mid-answer (a documented cause of one list visually splitting into two, since CommonMark treats a marker change as starting a new list) doesn't cause that split.
- Indentation scale is a flat 2 raw spaces (tabs expanded to 4 spaces) per nesting level, capped at 3 levels deep — this is the CommonMark-minimum nesting indent for a `-` marker (marker width 1 + 1 required space), and also what LLMs converge on in practice. See [`_lists.py`](python/src/llm_markdown_sanitizer/_lists.py) / [`ListFixer.java`](java/src/main/java/io/github/stlahxm/markdownsanitizer/ListFixer.java) for the exact derivation.
- A heavily indented (4+ spaces) plain-text line immediately following a near-top-level list item is treated as a wrapped continuation of that item and re-indented under it.
- **Not handled**: ordered lists (`1.`, `2.`, ...) are passed through untouched — this library only normalizes bullet lists.
- Lines inside a table row or an embedded code fence are never touched by list normalization.

### Missing blank line before a list or heading

A list or heading immediately following a non-blank paragraph line (one that isn't itself a list item, heading, or table row) gets a blank line inserted before it. Without this, CommonMark-strict renderers treat the list/heading as a continuation of the preceding paragraph and it renders as plain text instead of a structural element — one of the most commonly reported causes of "broken" LLM markdown ([anthropics/claude-code#17554](https://github.com/anthropics/claude-code/issues/17554)). Idempotent: does nothing if a blank line is already present, and never inserts one between consecutive list items or consecutive headings. See [#10](https://github.com/stlahxm/llm-markdown-sanitizer/issues/10).

### Missing space after `#` in headings

`#Heading` isn't a valid ATX heading per CommonMark — without a space after the `#` run it's just a paragraph starting with a literal `#`. Fixed to `# Heading` (through `######`), but only when a letter (Latin or Korean) immediately follows the `#` run — a digit or symbol there (`#1`, `#tag`) is left alone, since those are common in real prose as issue references or hashtags rather than a heading missing its space. See [#11](https://github.com/stlahxm/llm-markdown-sanitizer/issues/11).

### Smart quotes inside code

Typographic/"smart" quotes (`“` `”` `‘` `’`) are normalized to straight ASCII quotes inside fenced code blocks and inline `` `code` `` spans, since code with curly quotes instead of straight ones fails to compile/parse and looks wrong when copy-pasted. Prose text outside of code is deliberately left untouched — curly quotes there may be intentional stylistic output, not a bug. See [#12](https://github.com/stlahxm/llm-markdown-sanitizer/issues/12).

### Table repair

A block of consecutive `|`-prefixed lines is checked against three conditions before being kept:

1. at least one row is a valid GFM separator row (every cell matches `:?-{3,}:?`, e.g. `---` or `:--:`)
2. at least 2 non-separator (data) rows exist
3. every row in the block has the same cell count, **and** that count is greater than 1 — single-column "tables" are always dropped, since a lone `| text |` line is usually just a stray pipe character rather than an intentional table

Blocks that fail any of these are dropped entirely (along with a dangling `Some intro text:` line immediately before them, if present) rather than left to render as a wall of `|` characters.

Separately, a table collapsed onto a single line (every row's `|`-boundaries glued together with just whitespace between them) is expanded back into one row per line **before** the validity check above runs.

Cell/column counting respects the two GFM-valid ways to put a literal `|` inside a cell: backslash-escaped (`\|`) and inside inline code (`` `a|b` ``). A line-scanner tracks backtick code-span boundaries and escape sequences so neither kind of literal pipe gets miscounted as an extra column and drags the whole table into the "dropped as broken" path. See [#5](https://github.com/stlahxm/llm-markdown-sanitizer/issues/5) for the original bug report.

### Protecting your own syntax (`protectPatterns` / `protect_patterns`)

- Caller-supplied regex patterns are matched and fully protected from every pass above, applied in the order given, then restored verbatim.
- Uses an internal placeholder mechanism where each protection call — including the library's own internal math-span protection — gets an isolated namespace, so a caller's custom pattern can never collide with (and be silently erased by) the library's own internal bookkeeping. This was a real bug found and fixed during development; see the git history for `_protect.py` / `PlaceholderProtector.java`.

### Input handling

- `None` / empty input returns `""`.
- A list of `{"text": ...}`-shaped chunks (the shape some streaming LLM SDKs return) is concatenated before processing (Python only, via a plain `list` argument — the Java port takes a `String` directly).

### Explicitly out of scope

Headings, blockquotes, horizontal rules, inline code spans, links, and images are passed through untouched — they weren't observed as a source of broken LLM output in the production system this library was extracted from, so there was nothing to fix. If you hit a real-world broken-markdown case not covered above, please [open an issue](https://github.com/stlahxm/llm-markdown-sanitizer/issues) with the input that triggered it.

### Known limitation: math-span detection is a heuristic, not a parser

Inline math protection (see "`**bold**` spacing" above) can't tell "real" math apart from two unrelated dollar amounts on the same line using a grammar, since neither `$...$` nor `$$...$$` are part of the CommonMark/GFM spec — they're a convention different renderers interpret differently. To reduce false positives (e.g. `Item costs $5 and $10 total` being treated as one math span spanning both amounts), a candidate span is only protected if it doesn't contain two bare, plain-language words separated by nothing but whitespace — real math essentially never does, since LaTeX text is normally `\text{...}`-wrapped or joined by operators/braces rather than bare spaces. This is a heuristic, not a parser: it correctly handles the cases this library's test suite covers, but isn't guaranteed to classify every possible input correctly. When it declines to protect a span, the worst case is a `**bold**` inside real, unusual-looking math not getting its spacing fixed — not data loss.

## Contributing

Bug fixes and small improvements are welcome. No CLA/DCO required — see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines. AI coding agents should pick up [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) automatically.

## License

MIT
