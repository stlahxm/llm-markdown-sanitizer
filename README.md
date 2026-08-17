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

Yeah, that's really it. Defaults handle the common failure modes without you having to configure anything.

## Why this exists

Ask an LLM to answer in markdown and sooner or later you'll get: the whole answer wrapped in a stray ` ```markdown ` fence, `**bold**text` glued straight onto the next word, list indentation that's inconsistent within the same response, and tables that are either collapsed onto one line or just missing a separator row. Render any of that as-is and your UI breaks in some annoying way.

`clean_markdown()` fixes all of that in one left-to-right pass over the text — no whole-string regex backtracking, so it stays fast even on long documents.

## What it fixes

| Problem | Before | After |
|---|---|---|
| Wrapping code fence | ` ```markdown\n# Title\n``` ` | `# Title` |
| `<br>` outside tables | `Line one<br>Line two` | `Line one\nLine two` (left alone *inside* table cells — that's usually intentional there) |
| Bold glued to text | `**Note**this breaks` | `**Note** this breaks` |
| Inconsistent list indent | mixed 2/3/tab indents | normalized to 4 spaces per nesting level |
| Collapsed table | `\| A \| B \| \| --- \| --- \| \| 1 \| 2 \|` | proper one-row-per-line table |
| Broken table (no separator / mismatched columns) | renders as a wall of `\|` | dropped instead of rendering broken |

## Protecting your own syntax

If your prompts produce custom tokens — your own `[[wiki]]`-style syntax, template placeholders, whatever — that the cleanup passes above might mangle, just tell it to leave them alone:

```python
import re

clean_markdown(text, protect_patterns=[re.compile(r"\[\[.*?\]\]")])
```

## Where this came from

I pulled this out of the markdown-cleanup layer of a RAG service I run in production, after months of it quietly eating weird LLM output. The domain-specific bits — a custom wiki syntax, a Korean-language note pattern — got stripped out in favor of the `protect_patterns` option above, so anyone can plug in their own syntax instead of being stuck with mine.

## Contributing

Bug fixes and small improvements welcome, no CLA/DCO nonsense — just open a PR. [CONTRIBUTING.md](CONTRIBUTING.md) has the (short) details and how to run the tests. If you're pointing an AI coding agent at this repo, it should pick up [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) on its own.

## License

MIT
