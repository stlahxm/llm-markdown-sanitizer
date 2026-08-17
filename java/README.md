# llm-markdown-sanitizer (Java)

Fix broken markdown that LLMs generate. Zero runtime dependencies, one method.

A Python binding with the same behavior is also available — see the [repository root](..) for both.

## Install

Requires Java 17+. Distributed via [JitPack](https://jitpack.io/#stlahxm/llm-markdown-sanitizer) — no Maven Central publishing step involved, JitPack builds straight from the GitHub tag the first time anyone requests that version (can take a minute on the very first request for a given version, instant after that).

Gradle (Kotlin DSL, `build.gradle.kts`):

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.stlahxm:llm-markdown-sanitizer:java-v0.1.0")
}
```

Gradle (Groovy DSL, `build.gradle`):

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.stlahxm:llm-markdown-sanitizer:java-v0.1.0'
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
        <version>java-v0.1.0</version>
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
