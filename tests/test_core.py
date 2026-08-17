import re

from llm_markdown_sanitizer import clean_markdown


def test_empty_input_returns_empty_string():
    assert clean_markdown("") == ""
    assert clean_markdown(None) == ""


def test_strips_wrapping_markdown_code_fence():
    text = "```markdown\n# Title\n\nBody text.\n```"
    assert clean_markdown(text) == "# Title\n\nBody text."


def test_does_not_strip_fence_with_a_real_language_label():
    text = "```python\nprint('hi')\n```"
    assert clean_markdown(text) == text


def test_accepts_list_of_text_chunks():
    chunks = [{"text": "Hello "}, {"text": "world"}]
    assert clean_markdown(chunks) == "Hello world"


def test_br_tag_converted_to_newline_outside_table():
    text = "Line one<br>Line two"
    assert clean_markdown(text) == "Line one\nLine two"


def test_br_tag_preserved_inside_table_cell():
    text = "| A | B |\n| --- | --- |\n| line1<br>line2 | x |\n| y | z |"
    result = clean_markdown(text)
    assert "<br>" in result


def test_bold_glued_to_following_word_gets_a_space():
    text = "**Note**this needs a space"
    assert clean_markdown(text) == "**Note** this needs a space"


def test_bold_glued_to_preceding_word_gets_a_space():
    text = "before**Note**"
    assert clean_markdown(text) == "before **Note**"


def test_math_span_is_not_touched_by_emphasis_normalizer():
    text = "The value $a^{**}b$ should survive untouched"
    assert clean_markdown(text) == text


def test_list_indentation_is_normalized_to_four_spaces_per_level():
    text = "- top level\n  - nested with two spaces\n    - nested with four spaces"
    result = clean_markdown(text)
    lines = result.splitlines()
    assert lines[0] == "- top level"
    assert lines[1] == "    - nested with two spaces"
    assert lines[2] == "    - nested with four spaces"


def test_compact_single_line_table_is_expanded():
    text = "| A | B | | --- | --- | | 1 | 2 |"
    result = clean_markdown(text)
    lines = [line for line in result.splitlines() if line.strip()]
    assert len(lines) == 3
    assert lines[0].strip().startswith("|")
    assert lines[1].strip().startswith("|")


def test_table_missing_separator_row_is_dropped():
    text = "Intro text\n| A | B |\n| 1 | 2 |\nOutro text"
    result = clean_markdown(text)
    assert "| A | B |" not in result
    assert "Intro text" in result
    assert "Outro text" in result


def test_table_with_mismatched_column_counts_is_dropped():
    text = "| A | B |\n| --- | --- |\n| 1 |\n| 2 | 3 |"
    result = clean_markdown(text)
    assert "|" not in result


def test_well_formed_table_is_preserved():
    text = "| A | B |\n| --- | --- |\n| 1 | 2 |\n| 3 | 4 |"
    result = clean_markdown(text)
    assert result == text


def test_protect_patterns_leaves_custom_tokens_untouched():
    text = "before [[my:custom]]token**glued**after"
    pattern = re.compile(r"\[\[my:custom\]\]")
    result = clean_markdown(text, protect_patterns=[pattern])
    assert "[[my:custom]]" in result
