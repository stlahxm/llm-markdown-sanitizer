package io.github.stlahxm.markdownsanitizer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ported 1:1 from the Python package's tests/test_core.py, so behavior stays
 * identical across both language bindings.
 */
class MarkdownSanitizerTest {

    @Test
    void emptyInputReturnsEmptyString() {
        assertEquals("", MarkdownSanitizer.clean(""));
        assertEquals("", MarkdownSanitizer.clean(null));
    }

    @Test
    void stripsWrappingMarkdownCodeFence() {
        String text = "```markdown\n# Title\n\nBody text.\n```";
        assertEquals("# Title\n\nBody text.", MarkdownSanitizer.clean(text));
    }

    @Test
    void doesNotStripFenceWithRealLanguageLabel() {
        String text = "```python\nprint('hi')\n```";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }

    @Test
    void brTagConvertedToNewlineOutsideTable() {
        assertEquals("Line one\nLine two", MarkdownSanitizer.clean("Line one<br>Line two"));
    }

    @Test
    void brTagPreservedInsideTableCell() {
        String text = "| A | B |\n| --- | --- |\n| line1<br>line2 | x |\n| y | z |";
        assertTrue(MarkdownSanitizer.clean(text).contains("<br>"));
    }

    @Test
    void boldGluedToFollowingWordGetsSpace() {
        assertEquals("**Note** this needs a space", MarkdownSanitizer.clean("**Note**this needs a space"));
    }

    @Test
    void boldGluedToPrecedingWordGetsSpace() {
        assertEquals("before **Note**", MarkdownSanitizer.clean("before**Note**"));
    }

    @Test
    void mathSpanNotTouchedByEmphasisNormalizer() {
        String text = "The value $a^{**}b$ should survive untouched";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }

    @Test
    void singleLevelListIndentationNormalizedToFourSpaces() {
        String text = "- top level\n  - one nested item";
        String[] lines = MarkdownSanitizer.clean(text).split("\n");
        assertEquals("- top level", lines[0]);
        assertEquals("    - one nested item", lines[1]);
    }

    @Test
    void nestedListLevelsStayDistinct() {
        String text = "- top\n  - two-space (level 2)\n    - four-space (level 3)";
        String[] lines = MarkdownSanitizer.clean(text).split("\n");
        assertEquals("- top", lines[0]);
        assertEquals("    - two-space (level 2)", lines[1]);
        assertEquals("        - four-space (level 3)", lines[2]);
    }

    @Test
    void compactSingleLineTableIsExpanded() {
        String text = "| A | B | | --- | --- | | 1 | 2 |";
        String[] lines = MarkdownSanitizer.clean(text).split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].strip().startsWith("|"));
        assertTrue(lines[1].strip().startsWith("|"));
    }

    @Test
    void tableMissingSeparatorRowIsDropped() {
        String text = "Intro text\n| A | B |\n| 1 | 2 |\nOutro text";
        String result = MarkdownSanitizer.clean(text);
        assertFalse(result.contains("| A | B |"));
        assertTrue(result.contains("Intro text"));
        assertTrue(result.contains("Outro text"));
    }

    @Test
    void tableWithMismatchedColumnCountsIsDropped() {
        String text = "| A | B |\n| --- | --- |\n| 1 |\n| 2 | 3 |";
        assertFalse(MarkdownSanitizer.clean(text).contains("|"));
    }

    @Test
    void wellFormedTableIsPreserved() {
        String text = "| A | B |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }

    @Test
    void protectPatternsLeaveCustomTokensUntouched() {
        String text = "before [[my:custom]]token**glued**after";
        Pattern pattern = Pattern.compile("\\[\\[my:custom]]");
        String result = MarkdownSanitizer.clean(text, List.of(pattern));
        assertTrue(result.contains("[[my:custom]]"));
    }

    @Test
    void realisticDocumentEndToEnd() {
        String text = "```markdown\n"
                + "# Lecture Summary\n\n"
                + "**Note**this section covers async programming basics.<br>Make sure you understand the event loop first.\n\n"
                + "## Key Concepts\n\n"
                + "- Event Loop\n"
                + "  - Handles all async operations\n"
                + "    - Single threaded\n"
                + "- Coroutines\n"
                + "- Tasks\n\n"
                + "The math behind scheduling: $O(**n**)$ per tick, which should NOT be touched by the bold-fixer.\n\n"
                + "| Concept | Description | | --- | --- | | Event Loop | Core of asyncio | | Coroutine | Suspendable function |\n\n"
                + "Here's a broken table that's missing its separator row:\n"
                + "| Name | Value |\n"
                + "| foo | 1 |\n"
                + "| bar | 2 |\n\n"
                + "before**glued bold**after and one more：**tight**next\n"
                + "```";

        String result = MarkdownSanitizer.clean(text);

        assertFalse(result.startsWith("```"));
        assertFalse(result.stripTrailing().endsWith("```"));
        assertTrue(result.contains("**Note** this section"));
        assertTrue(result.contains("before **glued bold** after"));
        assertFalse(result.contains("<br>"));
        assertTrue(result.contains("programming basics.\nMake sure"));
        assertTrue(result.contains("$O(**n**)$"));
        assertTrue(result.contains("| Event Loop | Core of asyncio"));
        assertFalse(result.contains("| foo | 1 |"));
        assertFalse(result.contains("| bar | 2 |"));
    }

    @Test
    void koreanEmphasisBoundariesGetSpaced() {
        String text = "토큰은**JWT**형식입니다";
        assertEquals("토큰은 **JWT** 형식입니다", MarkdownSanitizer.clean(text));
    }

    @Test
    void emojiIsNotTreatedAsAlnumBoundary() {
        String text = "**bold**😀text";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }

    @Test
    void embeddedCodeFenceContentIsNeverTouched() {
        String text = "Intro text.\n\n"
                + "```python\n"
                + "def foo():\n"
                + "    - this is a code comment, not a list\n"
                + "    x = {'a': 1}\n"
                + "```\n\n"
                + "```\n"
                + "| not | a | table |\n"
                + "| just | code | comment |\n"
                + "```\n\n"
                + "Outro text.";
        String result = MarkdownSanitizer.clean(text);
        assertTrue(result.contains("    - this is a code comment, not a list"));
        assertTrue(result.contains("| not | a | table |"));
        assertTrue(result.contains("| just | code | comment |"));
    }

    @Test
    void unclosedDoubleAsteriskIsPreservedNotDropped() {
        String fenced = "```python\ndef foo(**kwargs):\n    return {**kwargs, 'x': 1}\n```";
        assertTrue(MarkdownSanitizer.clean(fenced).contains("def foo(**kwargs):"));
        assertTrue(MarkdownSanitizer.clean(fenced).contains("{**kwargs, 'x': 1}"));

        String prose = "Call it like foo(**kwargs) without a closing pair.";
        assertTrue(MarkdownSanitizer.clean(prose).contains("foo(**kwargs)"));
    }

    @Test
    void placeholderNeverLeaksIntoOutputWhenFollowedByADigit() {
        // A protected math span immediately followed by a literal digit
        // (e.g. two separate dollar amounts on the same line) used to make
        // the restore regex's \d+ swallow that trailing digit into the
        // placeholder's index, producing an out-of-range index and leaking
        // the raw "MDSAN0_010"-shaped placeholder straight into the output.
        String text = "This costs $5 and that one costs $10, so**buy**the cheaper one.";
        String result = MarkdownSanitizer.clean(text);
        assertFalse(result.contains("MDSAN"));
        assertEquals("This costs $5 and that one costs $10, so **buy** the cheaper one.", result);
    }

    @Test
    void tableWithEscapedPipeInACellIsPreserved() {
        // A cell containing a backslash-escaped pipe (valid GFM: `\|` is a
        // literal `|`, not a column boundary) used to make that row's cell
        // count disagree with the header/separator row, so the incomplete-
        // table remover dropped the entire otherwise-valid table.
        String text = "| A | B |\n| --- | --- |\n| a\\|b | c |\n| d | e |";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }
}
