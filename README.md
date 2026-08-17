# llm-markdown-sanitizer

Fix broken markdown that LLMs generate — tables, lists, emphasis, code fences. Zero dependencies, one function/method.

Extracted from the markdown-cleanup layer of a production RAG service, after months of hardening against real LLM output. Available for both Python and Java, sharing the same behavior and test fixtures.

- **[Python](python/)** — `pip install llm-markdown-sanitizer`
- **[Java](java/)** — via JitPack (`com.github.stlahxm:llm-markdown-sanitizer:java-<version>`)

Each subdirectory has its own README with install/usage details specific to that language. The core logic — code fence stripping, `<br>` handling, emphasis boundary fixes, list indentation, table repair — is ported 1:1 between the two, including bug fixes (see [CONTRIBUTING.md](CONTRIBUTING.md) for how changes should be kept in sync across both).

## Contributing

Bug fixes and small improvements are welcome. No CLA/DCO required — see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines. AI coding agents should pick up [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) automatically.

## License

MIT
