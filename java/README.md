# llm-markdown-sanitizer (Java)

[![JitPack](https://jitpack.io/v/stlahxm/llm-markdown-sanitizer.svg)](https://jitpack.io/#stlahxm/llm-markdown-sanitizer)
[![CI](https://img.shields.io/github/actions/workflow/status/stlahxm/llm-markdown-sanitizer/java-ci.yml?branch=main)](https://github.com/stlahxm/llm-markdown-sanitizer/actions/workflows/java-ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](https://github.com/stlahxm/llm-markdown-sanitizer/blob/main/LICENSE)

Fix broken markdown that LLMs generate — tables, lists, headings, emphasis, code fences, quotes. Zero runtime dependencies, one method.

A Python binding with the same behavior is also available — see the [repository root](https://github.com/stlahxm/llm-markdown-sanitizer) for both.

## Install

JitPack page (browse all available versions): **https://jitpack.io/#stlahxm/llm-markdown-sanitizer**

Requires Java 17+. Distributed via [JitPack](https://jitpack.io/#stlahxm/llm-markdown-sanitizer) — no Maven Central publishing step involved, JitPack builds straight from the GitHub tag the first time anyone requests that version (can take a minute on the very first request for a given version, instant after that).

Gradle (Kotlin DSL, `build.gradle.kts`):

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.stlahxm:llm-markdown-sanitizer:java-v0.2.2")
}
```

Gradle (Groovy DSL, `build.gradle`):

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.stlahxm:llm-markdown-sanitizer:java-v0.2.2'
}
```

Maven (`pom.xml`):

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.stlahxm</groupId>
        <artifactId>llm-markdown-sanitizer</artifactId>
        <version>java-v0.2.2</version>
    </dependency>
</dependencies>
```

Verify it resolved correctly:

```bash
./gradlew dependencies --configuration compileClasspath | grep llm-markdown-sanitizer
```

## Use

The whole API is one static method:

```java
import io.github.stlahxm.markdownsanitizer.MarkdownSanitizer;

MarkdownSanitizer.clean("**Note**this needs a space");
// "**Note** this needs a space"

MarkdownSanitizer.clean("| A | B | | --- | --- | | 1 | 2 |");
// "| A | B |\n| --- | --- |\n| 1 | 2 |"
```

### In a Spring Boot controller

The intended use case: a backend stores LLM-generated markdown (from a batch job, a separate AI service, wherever), and cleans it right before handing it to the frontend — rather than making every frontend independently work around inconsistent LLM formatting.

```java
@RestController
@RequestMapping("/api/lectures")
public class LectureSummaryController {

    private final LectureSummaryRepository repository;

    public LectureSummaryController(LectureSummaryRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<String> getSummary(@PathVariable Long id) {
        String rawMarkdown = repository.findAiSummaryById(id); // stored LLM output
        return ResponseEntity.ok(MarkdownSanitizer.clean(rawMarkdown));
    }
}
```

Since `MarkdownSanitizer.clean()` is a stateless static method with no I/O, it's safe to call directly wherever the response is being assembled — no bean, no configuration, nothing to wire up.

## Why this exists

If a Spring service stores LLM-generated content and serves it straight to a frontend, the same markdown problems that show up in the Python ecosystem show up here too: a stray ` ```markdown ` fence around the whole response (or left unclosed at the end), `**bold**text` glued onto the next word, a heading missing its space or its blank line, curly quotes inside a code sample, inconsistent list indentation, and tables that are either collapsed onto one line or missing a separator row. `MarkdownSanitizer.clean()` fixes all of that before the response goes out.

## What it fixes

| Problem | Before | After |
|---|---|---|
| Wrapping code fence | ` ```markdown\n# Title\n``` ` | `# Title` |
| `<br>` outside tables | `Line one<br>Line two` | `Line one\nLine two` (left untouched *inside* table cells) |
| Bold glued to text | `**Note**this breaks` | `**Note** this breaks` |
| Inconsistent list indent | mixed 2/3/tab indents | normalized to 4 spaces per nesting level |
| Collapsed table | `\| A \| B \| \| --- \| --- \| \| 1 \| 2 \|` | proper one-row-per-line table |
| Broken table (no separator / mismatched columns) | renders as a wall of `\|` | dropped instead of rendering broken |
| `\|` inside a table cell (escaped or in inline code) | miscounted as an extra column, table dropped | preserved, table kept |
| Missing blank line before a list/heading | renders as a paragraph continuation | blank line inserted |
| Missing space after `#` | `#Heading` stays plain text | `# Heading` |
| Smart quotes inside code | `` `print(“hi”)` `` fails to parse | `` `print("hi")` `` |
| Unclosed trailing code fence | rest of the answer swallowed as code | closing fence appended |

## Protecting your own syntax

```java
import java.util.List;
import java.util.regex.Pattern;

MarkdownSanitizer.clean(text, List.of(Pattern.compile("\\[\\[.*?]]")));
```

## Build locally

```bash
cd java
./gradlew build
```

## Origin

Ported from the [Python version](../python) of this package, sharing the same behavior and test fixtures. Both sides are kept in lockstep as bugs and new fixes land in either language — see [AGENTS.md](../AGENTS.md) for the cross-port rule.

## Contributing

See the [repository root's CONTRIBUTING.md](../CONTRIBUTING.md).

## License

MIT
