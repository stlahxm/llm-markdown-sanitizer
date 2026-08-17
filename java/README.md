# llm-markdown-sanitizer (Java)

Fix broken markdown that LLMs generate. Zero runtime dependencies, one method.

A Python binding with the same behavior is also available — see the [repository root](..) for both.

## Install

Via [JitPack](https://jitpack.io/#stlahxm/llm-markdown-sanitizer) (Gradle):

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.stlahxm:llm-markdown-sanitizer:java-v0.1.0")
}
```

Maven:

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.stlahxm</groupId>
    <artifactId>llm-markdown-sanitizer</artifactId>
    <version>java-v0.1.0</version>
</dependency>
```

## Use

```java
import io.github.stlahxm.markdownsanitizer.MarkdownSanitizer;

MarkdownSanitizer.clean("**Note**this needs a space");
// "**Note** this needs a space"

MarkdownSanitizer.clean("| A | B | | --- | --- | | 1 | 2 |");
// "| A | B |\n| --- | --- |\n| 1 | 2 |"
```

## Why this exists

If a Spring service stores LLM-generated content and serves it straight to a frontend, the same markdown problems that show up in the Python ecosystem show up here too: a stray ` ```markdown ` fence around the whole response, `**bold**text` glued onto the next word, inconsistent list indentation, and tables that are either collapsed onto one line or missing a separator row. `MarkdownSanitizer.clean()` fixes all of that before the response goes out.

## What it fixes

| Problem | Before | After |
|---|---|---|
| Wrapping code fence | ` ```markdown\n# Title\n``` ` | `# Title` |
| `<br>` outside tables | `Line one<br>Line two` | `Line one\nLine two` (left untouched *inside* table cells) |
| Bold glued to text | `**Note**this breaks` | `**Note** this breaks` |
| Inconsistent list indent | mixed 2/3/tab indents | normalized to 4 spaces per nesting level |
| Collapsed table | `\| A \| B \| \| --- \| --- \| \| 1 \| 2 \|` | proper one-row-per-line table |
| Broken table (no separator / mismatched columns) | renders as a wall of `\|` | dropped instead of rendering broken |

## Protecting your own syntax

```java
import java.util.List;
import java.util.regex.Pattern;

MarkdownSanitizer.clean(text, List.of(Pattern.compile("\\[\\[.*?]]")));
```

## Build locally

```bash
cd java
gradle build
```

## Origin

Ported from the [Python version](../python) of this package, keeping the same behavior and test fixtures. The list-indentation and placeholder-namespace bugs found and fixed there (see the repository's issue history) were already fixed at the time of this port.

## Contributing

See the [repository root's CONTRIBUTING.md](../CONTRIBUTING.md).

## License

MIT
