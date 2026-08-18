package io.github.stlahxm.markdownsanitizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ported 1:1 from the Python package's tests/test_edge_cases.py. Edge cases
 * discussed and manually verified during development but not previously
 * captured as regression tests: Unicode emphasis-boundary behavior (Korean,
 * emoji, symbols, accented Latin, multi-codepoint emoji), incidental
 * protections that aren't guaranteed, and documented known limitations.
 * Locking all of these in as tests means a future change that silently
 * alters this behavior gets caught, whether it's a regression or a
 * deliberate improvement that should update the assertion here too.
 */
class EdgeCasesTest {

    // --- Unicode alnum-boundary behavior (see EmphasisFixer.isAlnumBoundary) ---

    @Test
    void koreanTextGetsEmphasisSpacingLikeAscii() {
        assertEquals("토큰은 **JWT** 형식입니다", MarkdownSanitizer.clean("토큰은**JWT**형식입니다"));
    }

    @Test
    void accentedLatinTextGetsEmphasisSpacing() {
        assertEquals("café **important** word", MarkdownSanitizer.clean("café**important**word"));
    }

    @Test
    void emojiIsNotAnAlnumBoundaryNoSpaceInserted() {
        // "**bold**" immediately followed by an emoji: emoji is not
        // alphanumeric, so no space is inserted there (unlike a real letter/digit).
        String text = "**중요**😀내용";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }

    @Test
    void emojiBeforeBoldDoesNotBlockTheOtherBoundary() {
        // The emoji is on the left, non-alnum, so no space is added there --
        // but the trailing boundary (a real Korean character) still gets fixed.
        assertEquals("😀**중요** 내용", MarkdownSanitizer.clean("😀**중요**내용"));
    }

    @Test
    void symbolIsNotAnAlnumBoundary() {
        assertEquals("copyright©**bold** mark", MarkdownSanitizer.clean("copyright©**bold**mark"));
    }

    @Test
    void multiCodepointZwjEmojiSequenceDoesNotCrashOrCorrupt() {
        // A ZWJ (zero-width joiner) emoji sequence like a family emoji is
        // several Unicode code points glued together. This should be
        // handled without raising and without splitting the sequence apart.
        String family = "👨‍👩‍👧‍👦";
        String result = MarkdownSanitizer.clean("before**bold**" + family + "after");
        assertTrue(result.startsWith("before **bold**"));
        assertTrue(result.contains(family));
    }

    // --- Incidental (not guaranteed) protections ---

    @Test
    void boldInsideInlineCodeHappensToSurviveDueToBacktickBoundary() {
        // Backticks aren't alphanumeric, so `**not_bold**` inside inline
        // code survives unchanged -- but this is a side effect of the
        // boundary rule, not deliberate inline-code-span awareness. A case
        // like `x**bold**y` (letters touching the markers on both sides,
        // still inside backticks) would NOT be protected. Inline code
        // spans are not actually parsed; see the README's "Explicitly out
        // of scope" section.
        String text = "Use `**not_bold**` literally.";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }

    // --- Documented known limitations (see README "Known limitation" sections) ---

    @Test
    void knownLimitationTripleAsteriskIsPassedThroughUnchanged() {
        // `***bold italic***` (combined bold+italic) is not a pattern this
        // library specifically targets -- it's neither corrupted nor fixed.
        String text = "This is ***bold italic***text here.";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }

    @Test
    void knownLimitationPipeInsideInlineCodeInATableCellStillDropsTheTable() {
        // Unlike a backslash-escaped pipe (fixed, see issue #5), a `|`
        // inside inline code within a table cell isn't recognized as
        // non-tabular -- it still gets miscounted as an extra column and
        // the table is dropped. Fixing this requires tracking backtick
        // code-span boundaries within a line, deferred as noted in the
        // README's Table repair section.
        String text = "| Code | Desc |\n| --- | --- |\n| `a|b` | pipe in code |";
        assertEquals("", MarkdownSanitizer.clean(text));
    }

    @Test
    void knownLimitationBoldBetweenTwoUnrelatedDollarAmountsIsNotFixed() {
        // Math-span detection treats any `$...$` pair as protected math,
        // since `$...$` isn't part of the CommonMark/GFM spec and there's
        // no authoritative grammar to distinguish "real" math from two
        // unrelated dollar amounts on the same line. A `**bold**` span
        // falling between them is conservatively left alone rather than
        // risking mangling real math.
        String text = "Item costs $5**important**note and $10 total.";
        assertEquals(text, MarkdownSanitizer.clean(text));
    }
}
