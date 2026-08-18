package io.github.stlahxm.markdownsanitizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ported 1:1 from the Python package's tests/test_common_llm_bugs.py.
 * Regression tests for markdown bugs specifically documented as common in
 * LLM-generated output (ChatGPT/Claude/Gemini), found via a research pass
 * over developer community threads and public issue trackers (see the
 * README's "What it fixes" table and linked issues for sources).
 */
class CommonLlmBugsTest {

    // --- Missing blank line before a list/heading ---

    @Test
    void blankLineInsertedBeforeListFollowingAParagraph() {
        assertEquals("Here are the steps:\n\n- First\n- Second",
                MarkdownSanitizer.clean("Here are the steps:\n- First\n- Second"));
    }

    @Test
    void blankLineInsertedBeforeHeadingFollowingAParagraph() {
        assertEquals("Some intro text.\n\n## Next Section",
                MarkdownSanitizer.clean("Some intro text.\n## Next Section"));
    }

    @Test
    void noBlankLineInsertedBetweenConsecutiveListItems() {
        String text = "- First\n- Second\n- Third";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }

    @Test
    void noExtraBlankLineInsertedWhenOneAlreadyExists() {
        String text = "Intro.\n\n- First\n- Second";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }

    // --- Missing space after `#` in an ATX heading ---

    @Test
    void headingMissingSpaceGetsOne() {
        assertEquals("# Heading\ncontent", MarkdownSanitizer.clean("#Heading\ncontent"));
    }

    @Test
    void headingLevelTwoMissingSpaceGetsOne() {
        assertEquals("## Section", MarkdownSanitizer.clean("##Section"));
    }

    @Test
    void numericHashReferenceIsNotTreatedAsAHeading() {
        // "#1" (issue/PR reference) and "#tag" (hashtag-style) should not
        // be reinterpreted as a heading missing its space -- only a letter
        // immediately after the `#` run is treated as heading-shaped.
        String text = "See #1 issue and #tag reference.";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }

    // --- Inconsistent bullet markers (-, *, +) splitting one list into two ---

    @Test
    void inconsistentBulletMarkersAreNormalizedToDash() {
        assertEquals("- item one\n- item two\n- item three",
                MarkdownSanitizer.clean("- item one\n* item two\n+ item three"));
    }

    // --- Smart/curly quotes inside code (fenced and inline) ---

    @Test
    void smartQuotesNormalizedInsideFencedCodeBlock() {
        assertEquals("```python\nprint(\"hello\")\n```",
                MarkdownSanitizer.clean("```python\nprint(“hello”)\n```"));
    }

    @Test
    void smartQuotesNormalizedInsideInlineCodeSpan() {
        assertEquals("Run `print(\"hello\")` now.",
                MarkdownSanitizer.clean("Run `print(“hello”)` now."));
    }

    @Test
    void smartQuotesInPlainProseAreLeftUntouched() {
        // Curly quotes outside of code may be intentional stylistic
        // output, not a bug -- only code gets normalized.
        String text = "She said “hello” to me.";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }

    @Test
    void smartSingleQuoteApostropheNormalizedInsideCode() {
        assertEquals("Use `don't_do_this()` here.",
                MarkdownSanitizer.clean("Use `don’t_do_this()` here."));
    }

    // --- Unclosed trailing code fence ---

    @Test
    void unclosedTrailingFenceGetsAutoClosed() {
        assertEquals("Some text\n```python\ndef f(): pass\n```",
                MarkdownSanitizer.clean("Some text\n```python\ndef f(): pass"));
    }

    @Test
    void properlyClosedFenceIsNotTouched() {
        String text = "Some text\n```python\ndef f(): pass\n```\nMore text";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }
}
