"""Edge cases discussed and manually verified during development but not
previously captured as regression tests: Unicode emphasis-boundary
behavior (Korean, emoji, symbols, accented Latin, multi-codepoint emoji),
incidental protections that aren't guaranteed, and documented known
limitations. Locking all of these in as tests means a future change that
silently alters this behavior gets caught, whether it's a regression or
a deliberate improvement that should update the assertion here too.
"""

from llm_markdown_sanitizer import clean_markdown

# --- Unicode alnum-boundary behavior (see _emphasis.py / _is_alnum_boundary) ---


def test_korean_text_gets_emphasis_spacing_like_ascii():
    assert clean_markdown("토큰은**JWT**형식입니다") == "토큰은 **JWT** 형식입니다"


def test_accented_latin_text_gets_emphasis_spacing():
    assert clean_markdown("café**important**word") == "café **important** word"


def test_emoji_is_not_an_alnum_boundary_no_space_inserted():
    # "**bold**" immediately followed by an emoji: emoji is not alphanumeric,
    # so no space is inserted there (unlike a real letter/digit).
    assert clean_markdown("**중요**😀내용") == "**중요**😀내용"


def test_emoji_before_bold_does_not_block_the_other_boundary():
    # The emoji is on the left, non-alnum, so no space is added there --
    # but the trailing boundary (a real Korean character) still gets fixed.
    assert clean_markdown("😀**중요**내용") == "😀**중요** 내용"


def test_symbol_is_not_an_alnum_boundary():
    assert clean_markdown("copyright©**bold**mark") == "copyright©**bold** mark"


def test_multi_codepoint_zwj_emoji_sequence_does_not_crash_or_corrupt():
    # A ZWJ (zero-width joiner) emoji sequence like a family emoji is
    # several Unicode code points glued together. This should be handled
    # without raising and without splitting the sequence apart.
    text = "before**bold**\U0001f468‍\U0001f469‍\U0001f467‍\U0001f466after"
    result = clean_markdown(text)
    assert result.startswith("before **bold**")
    assert "\U0001f468‍\U0001f469‍\U0001f467‍\U0001f466" in result


# --- Incidental (not guaranteed) protections ---


def test_bold_inside_inline_code_happens_to_survive_due_to_backtick_boundary():
    """Backticks aren't alphanumeric, so `**not_bold**` inside inline code
    survives unchanged -- but this is a side effect of the boundary rule,
    not deliberate inline-code-span awareness. A case like `x**bold**y`
    (letters touching the markers on both sides, still inside backticks)
    would NOT be protected. Inline code spans are not actually parsed;
    see the "Explicitly out of scope" section of the README."""
    text = "Use `**not_bold**` literally."
    assert clean_markdown(text) == text


# --- Documented known limitations (see README "Known limitation" sections) ---


def test_triple_asterisk_bold_italic_gets_emphasis_spacing_too():
    """`***bold italic***` (combined bold+italic) glued to surrounding text
    has the same rendering-ambiguity problem as `**bold**text` and gets the
    same boundary-space treatment, matched as a same-width `***...***` pair
    (checked before the `**` case, since `**` is a prefix of `***`)."""
    assert clean_markdown("This is ***bold italic***text here.") == "This is ***bold italic*** text here."


def test_pipe_inside_inline_code_in_a_table_cell_is_preserved():
    """A `|` inside inline code within a table cell (`` `a|b` ``) is valid
    GFM and must not be miscounted as an extra column -- previously this
    dropped the entire otherwise-valid table (issue #5 follow-up)."""
    text = "| Code | Desc |\n| --- | --- |\n| `a|b` | pipe in code |"
    assert clean_markdown(text) == text


def test_bold_between_two_unrelated_dollar_amounts_gets_fixed():
    """Two bare, plain-language words separated only by whitespace inside
    a `$...$` candidate span (e.g. "note and") is a strong signal it's
    prose with incidental dollar signs, not real math -- real math almost
    never has bare space-separated words, since LaTeX text is normally
    `\\text{...}`-wrapped or joined by operators/braces. Such candidates
    are declined from math protection so the emphasis normalizer still
    runs on them."""
    text = "Item costs $5**important**note and $10 total."
    assert clean_markdown(text) == "Item costs $5 **important** note and $10 total."


def test_real_math_with_latex_text_commands_is_still_protected():
    r"""A guard against the heuristic above being too aggressive: `\text{...}`
    LaTeX commands joined by operators/braces (not bare whitespace) should
    still be recognized as real math and left untouched."""
    text = r"formula $\text{distance} = \text{speed}\times t$ done"
    assert clean_markdown(text) == text
