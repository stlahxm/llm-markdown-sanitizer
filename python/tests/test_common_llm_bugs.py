"""Regression tests for markdown bugs specifically documented as common in
LLM-generated output (ChatGPT/Claude/Gemini), found via a research pass
over developer community threads and public issue trackers (see the
README's "What it fixes" table and linked issues for sources). Each of
these is a distinct structural problem from the ones already covered
elsewhere in the test suite (tables, list indentation, bold spacing).
"""

from llm_markdown_sanitizer import clean_markdown


# --- Missing blank line before a list/heading (CommonMark-strict renderers
# treat it as paragraph continuation otherwise) ---


def test_blank_line_inserted_before_list_following_a_paragraph():
    text = "Here are the steps:\n- First\n- Second"
    assert clean_markdown(text) == "Here are the steps:\n\n- First\n- Second"


def test_blank_line_inserted_before_heading_following_a_paragraph():
    text = "Some intro text.\n## Next Section"
    assert clean_markdown(text) == "Some intro text.\n\n## Next Section"


def test_no_blank_line_inserted_between_consecutive_list_items():
    text = "- First\n- Second\n- Third"
    assert clean_markdown(text) == text


def test_no_extra_blank_line_inserted_when_one_already_exists():
    text = "Intro.\n\n- First\n- Second"
    assert clean_markdown(text) == text


# --- Missing space after `#` in an ATX heading ---


def test_heading_missing_space_gets_one():
    assert clean_markdown("#Heading\ncontent") == "# Heading\ncontent"


def test_heading_level_two_missing_space_gets_one():
    assert clean_markdown("##Section") == "## Section"


def test_numeric_hash_reference_is_not_treated_as_a_heading():
    """"#1" (issue/PR reference) and "#tag" (hashtag-style) should not be
    reinterpreted as a heading missing its space -- only a letter
    immediately after the `#` run is treated as heading-shaped."""
    text = "See #1 issue and #tag reference."
    assert clean_markdown(text) == text


# --- Inconsistent bullet markers (-, *, +) splitting one list into two ---


def test_inconsistent_bullet_markers_are_normalized_to_dash():
    text = "- item one\n* item two\n+ item three"
    assert clean_markdown(text) == "- item one\n- item two\n- item three"


# --- Smart/curly quotes inside code (fenced and inline) ---


def test_smart_quotes_normalized_inside_fenced_code_block():
    text = "```python\nprint(“hello”)\n```"
    assert clean_markdown(text) == '```python\nprint("hello")\n```'


def test_smart_quotes_normalized_inside_inline_code_span():
    assert clean_markdown("Run `print(“hello”)` now.") == 'Run `print("hello")` now.'


def test_smart_quotes_in_plain_prose_are_left_untouched():
    """Curly quotes outside of code may be intentional stylistic output,
    not a bug -- only code gets normalized."""
    text = "She said “hello” to me."
    assert clean_markdown(text) == text


def test_smart_single_quotes_apostrophe_normalized_inside_code():
    assert clean_markdown("Use `don’t_do_this()` here.") == "Use `don't_do_this()` here."


# --- Unclosed trailing code fence ---


def test_unclosed_trailing_fence_gets_auto_closed():
    text = "Some text\n```python\ndef f(): pass"
    assert clean_markdown(text) == "Some text\n```python\ndef f(): pass\n```"


def test_properly_closed_fence_is_not_touched():
    text = "Some text\n```python\ndef f(): pass\n```\nMore text"
    assert clean_markdown(text) == text
