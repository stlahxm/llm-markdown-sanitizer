# llm-markdown-sanitizer (Python)

Fix broken markdown that LLMs generate. Zero dependencies, one function.

A Java binding with the same behavior is also available — see the [repository root](..) for both.

## Install

```bash
pip install llm-markdown-sanitizer
```

## Use

```python
from llm_markdown_sanitizer import clean_markdown

clean_markdown("**Note**this needs a space")
# "**Note** this needs a space"

clean_markdown("| A | B | | --- | --- | | 1 | 2 |")
# "| A | B |\n| --- | --- |\n| 1 | 2 |"
```

Default settings handle the common failure modes without additional configuration.

## Why this exists

Ask an LLM to answer in markdown and eventually you'll get: the whole answer wrapped in a stray ` ```markdown ` fence, `**bold**text` glued directly onto the next word, list indentation that's inconsistent within the same response, and tables that are either collapsed onto one line or missing a separator row. Rendering that output as-is breaks the UI.

`clean_markdown()` fixes all of the above in a single left-to-right pass over the text — no whole-string regex backtracking, so it stays fast on long documents.

## What it fixes

| Problem | Before | After |
|---|---|---|
| Wrapping code fence | ` ```markdown\n# Title\n``` ` | `# Title` |
| `<br>` outside tables | `Line one<br>Line two` | `Line one\nLine two` (left untouched *inside* table cells, where it's usually intentional) |
| Bold glued to text | `**Note**this breaks` | `**Note** this breaks` |
| Inconsistent list indent | mixed 2/3/tab indents | normalized to 4 spaces per nesting level |
| Collapsed table | `\| A \| B \| \| --- \| --- \| \| 1 \| 2 \|` | proper one-row-per-line table |
| Broken table (no separator / mismatched columns) | renders as a wall of `\|` | dropped instead of rendering broken |

## Protecting your own syntax

If your prompts produce custom tokens — a `[[wiki]]`-style syntax, template placeholders, etc. — that the cleanup passes above might mangle, they can be excluded explicitly:

```python
import re

clean_markdown(text, protect_patterns=[re.compile(r"\[\[.*?\]\]")])
```

## Origin

Extracted from the markdown-cleanup layer of a production RAG service, after months of hardening against real LLM output. The domain-specific parts — a custom wiki syntax, a Korean-language note pattern — were removed in favor of the general `protect_patterns` mechanism above, so callers can supply their own domain syntax instead.

## Contributing

Bug fixes and small improvements are welcome. No CLA/DCO required — see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines and how to run the test suite locally. AI coding agents should pick up [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) automatically.

## License

MIT
