# llm-markdown-sanitizer

Fix broken markdown that LLMs generate. Zero dependencies, one function.

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

That's it. Default settings handle the common failure modes.

## Why

Ask an LLM to answer in markdown and you'll eventually get: a whole answer wrapped in a stray ` ```markdown ` fence, `**bold**text` glued directly onto the next word, list indentation that's inconsistent within the same response, and tables that are either collapsed onto one line or missing a separator row entirely. Rendering that straight through breaks your UI.

`clean_markdown()` fixes all of the above in a single left-to-right pass over the text (no whole-string regex backtracking, so it stays fast on long documents).

## What it fixes

| Problem | Before | After |
|---|---|---|
| Wrapping code fence | ` ```markdown\n# Title\n``` ` | `# Title` |
| `<br>` outside tables | `Line one<br>Line two` | `Line one\nLine two` (left untouched *inside* table cells, where it's intentional) |
| Bold glued to text | `**Note**this breaks` | `**Note** this breaks` |
| Inconsistent list indent | mixed 2/3/tab indents | normalized to 4 spaces per level |
| Collapsed table | `\| A \| B \| \| --- \| --- \| \| 1 \| 2 \|` | proper one-row-per-line table |
| Broken table (no separator / mismatched columns) | renders as a wall of `\|` | dropped instead of rendered broken |

## Protecting your own syntax

If your prompts produce custom tokens (your own `[[wiki]]`-style syntax, template placeholders, etc.) that the cleanup passes above might mangle, pass them in and they're left completely untouched:

```python
import re

clean_markdown(text, protect_patterns=[re.compile(r"\[\[.*?\]\]")])
```

## Origin

Extracted from the markdown-cleanup layer of a production RAG service, after months of hardening against real LLM output. The domain-specific bits (a custom wiki syntax, a Korean-language note pattern) were stripped out in favor of the general `protect_patterns` mechanism above, which anyone can use for their own domain syntax.

## Contributing

Bug fixes and small improvements are welcome — no CLA/DCO, just open a PR. See [CONTRIBUTING.md](CONTRIBUTING.md) for the (short) guidelines and how to run the test suite locally.

## License

MIT
