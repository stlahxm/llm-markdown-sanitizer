# llm-markdown-sanitizer (Python)

[![PyPI](https://img.shields.io/pypi/v/llm-markdown-sanitizer)](https://pypi.org/project/llm-markdown-sanitizer/)
[![Python versions](https://img.shields.io/pypi/pyversions/llm-markdown-sanitizer)](https://pypi.org/project/llm-markdown-sanitizer/)
[![Downloads](https://img.shields.io/pypi/dm/llm-markdown-sanitizer)](https://pypi.org/project/llm-markdown-sanitizer/)
[![CI](https://img.shields.io/github/actions/workflow/status/stlahxm/llm-markdown-sanitizer/python-ci.yml?branch=main)](https://github.com/stlahxm/llm-markdown-sanitizer/actions/workflows/python-ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](https://github.com/stlahxm/llm-markdown-sanitizer/blob/main/LICENSE)

Fix broken markdown that LLMs generate. Zero dependencies, one function.

A Java binding with the same behavior is also available — see the [repository root](https://github.com/stlahxm/llm-markdown-sanitizer) for both.

## Install

PyPI page: **https://pypi.org/project/llm-markdown-sanitizer/** (release history, file hashes, full metadata).

Requires Python 3.9+. No other dependencies get pulled in.

```bash
pip install llm-markdown-sanitizer
```

Using `uv` (if that's your workflow):

```bash
uv add llm-markdown-sanitizer
```

Using a virtual environment (recommended for any real project):

```bash
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install llm-markdown-sanitizer
```

Pin a specific version if you want reproducible builds — see the [PyPI release history](https://pypi.org/project/llm-markdown-sanitizer/#history) for available versions:

```bash
pip install "llm-markdown-sanitizer==0.1.5"
```

Add it to `requirements.txt` / `pyproject.toml` the normal way:

```
llm-markdown-sanitizer>=0.1.5
```

Verify it installed correctly:

```bash
python -c "from llm_markdown_sanitizer import clean_markdown; print(clean_markdown('**hi**there'))"
# **hi** there
```

## Use

The whole API is one function:

```python
from llm_markdown_sanitizer import clean_markdown

clean_markdown("**Note**this needs a space")
# "**Note** this needs a space"

clean_markdown("| A | B | | --- | --- | | 1 | 2 |")
# "| A | B |\n| --- | --- |\n| 1 | 2 |"
```

Default settings handle the common failure modes without additional configuration.

### In a FastAPI endpoint

A typical place to call this is right before a stored or freshly-generated LLM response goes out to a client:

```python
from fastapi import FastAPI
from llm_markdown_sanitizer import clean_markdown

app = FastAPI()

@app.get("/lectures/{lecture_id}/summary")
def get_summary(lecture_id: int):
    raw = db.get_ai_summary(lecture_id)  # however you fetch/generate it
    return {"summary": clean_markdown(raw)}
```

### Streaming/multi-part LLM responses

Some SDKs return responses as a list of `{"text": ...}`-shaped chunks instead of one string. `clean_markdown` accepts that directly:

```python
chunks = [{"text": "# Hello"}, {"text": "\n\nWorld"}]
clean_markdown(chunks)
# "# Hello\n\nWorld"
```

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

Bug fixes and small improvements are welcome. No CLA/DCO required — see [CONTRIBUTING.md](https://github.com/stlahxm/llm-markdown-sanitizer/blob/main/CONTRIBUTING.md) for guidelines and how to run the test suite locally. AI coding agents should pick up [AGENTS.md](https://github.com/stlahxm/llm-markdown-sanitizer/blob/main/AGENTS.md) automatically.

## License

MIT
