package io.github.stlahxm.markdownsanitizer;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cross-language parity: every fixture in {@code fixtures/} at the repo
 * root must produce the same output here as it does in the Python
 * package's {@code test_parity.py}. This is the single source of truth
 * both languages are checked against -- unlike the hand-written tests
 * elsewhere in this directory (and their Python counterparts), which can
 * drift silently when a fix lands in only one language. See
 * {@code fixtures/README.md} for the file format.
 */
class ParityTest {

    private static final Path FIXTURES_DIR = Paths.get("../fixtures");

    private record Fixture(String name, String input, String expected) {}

    private static Fixture parseFixture(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        String[] afterInputMarker = text.split("@@@INPUT@@@\n", 2);
        String[] afterExpectedMarker = afterInputMarker[1].split("@@@EXPECTED@@@\n", 2);
        String[] afterEndMarker = afterExpectedMarker[1].split("@@@END@@@\n", 2);
        String input = afterExpectedMarker[0];
        String expected = afterEndMarker[0];
        // Strip exactly the one trailing newline the heredoc format adds
        // before the next marker -- not part of the fixture's content.
        input = input.substring(0, input.length() - 1);
        expected = expected.substring(0, expected.length() - 1);
        return new Fixture(path.getFileName().toString(), input, expected);
    }

    private static List<Fixture> loadFixtures() {
        try (Stream<Path> paths = Files.list(FIXTURES_DIR)) {
            List<Path> txtFiles = paths.filter(p -> p.toString().endsWith(".txt")).sorted(Comparator.naturalOrder()).toList();
            List<Fixture> fixtures = new ArrayList<>();
            for (Path p : txtFiles) {
                fixtures.add(parseFixture(p));
            }
            return fixtures;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestFactory
    Stream<DynamicTest> fixtureMatchesExpectedOutput() {
        return loadFixtures().stream().map(fixture ->
                DynamicTest.dynamicTest(fixture.name(), () ->
                        assertEquals(fixture.expected(), MarkdownSanitizer.clean(fixture.input()))));
    }
}
