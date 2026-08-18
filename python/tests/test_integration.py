"""End-to-end tests against a realistic, multi-problem markdown blob —
not synthetic one-liners, but the shape of what actually comes back from
an LLM in production (code fence wrapper, several unrelated issues in the
same document).
"""

from llm_markdown_sanitizer import clean_markdown

REALISTIC_BROKEN_MARKDOWN = """```markdown
# Lecture Summary

**Note**this section covers async programming basics.<br>Make sure you understand the event loop first.

## Key Concepts

- Event Loop
  - Handles all async operations
    - Single threaded
- Coroutines
- Tasks

The math behind scheduling: $O(**n**)$ per tick, which should NOT be touched by the bold-fixer.

| Concept | Description | | --- | --- | | Event Loop | Core of asyncio | | Coroutine | Suspendable function |

Here's a broken table that's missing its separator row:
| Name | Value |
| foo | 1 |
| bar | 2 |

before**glued bold**after and one more：**tight**next
```"""


def test_realistic_document_end_to_end():
    result = clean_markdown(REALISTIC_BROKEN_MARKDOWN)

    # code fence unwrapped
    assert not result.startswith("```")
    assert not result.rstrip().endswith("```")

    # bold glued to surrounding text gets separated
    assert "**Note** this section" in result
    assert "before **glued bold** after" in result

    # <br> converted to a real newline outside a table
    assert "<br>" not in result
    assert "programming basics.\nMake sure" in result

    # math span survives untouched even though it contains "**"
    assert "$O(**n**)$" in result

    # compact single-line table gets split into real rows
    assert "| Event Loop | Core of asyncio" in result
    assert result.count("\n| ") >= 2  # at least a couple of real table rows

    # table missing a separator row is dropped, not rendered broken
    assert "| foo | 1 |" not in result
    assert "| bar | 2 |" not in result


def test_nested_list_levels_stay_distinct():
    """Regression test for issue #1: 2-space and 4-space indented nested
    list lines used to collapse onto the same output level because the
    indent-level calculation switched between a //4 and a //2 scale
    depending on whether the raw indent happened to be a multiple of 4.
    Fixed by using a single CommonMark-minimum 2-spaces-per-level scale
    (see _lists.py module docstring for the spec citation)."""
    text = "- top\n  - two-space (level 2)\n    - four-space (level 3)"
    result = clean_markdown(text)
    lines = result.splitlines()
    assert lines[0] == "- top"
    assert lines[1] == "    - two-space (level 2)"
    assert lines[2] == "        - four-space (level 3)"


def test_embedded_code_fence_content_is_never_touched():
    """Regression test for issue #2: content inside a code fence that
    ISN'T the whole-document wrapper (e.g. a code sample in the middle of
    an answer) used to be processed like ordinary prose -- a `-`-prefixed
    comment line got re-indented as a list item, and a `|`-prefixed
    comment/table-look-alike got deleted entirely by the incomplete-table
    remover, since neither pass knew it was looking at verbatim code."""
    text = (
        "Intro text.\n\n"
        "```python\n"
        "def foo():\n"
        "    - this is a code comment, not a list\n"
        "    x = {'a': 1}\n"
        "```\n\n"
        "```\n"
        "| not | a | table |\n"
        "| just | code | comment |\n"
        "```\n\n"
        "Outro text."
    )
    result = clean_markdown(text)
    assert "    - this is a code comment, not a list" in result
    assert "| not | a | table |" in result
    assert "| just | code | comment |" in result


def test_unclosed_double_asterisk_is_preserved_not_dropped():
    """Regression test for issue #2: a `**` with no matching close on the
    same line (e.g. Python's `**kwargs` or dict-unpacking `**`) used to be
    silently deleted instead of left as literal text. Exercised both inside
    a code fence (protected by the fence-tracking fix) and in plain prose
    outside one (protected by the emphasis-normalizer fix directly)."""
    fenced = "```python\ndef foo(**kwargs):\n    return {**kwargs, 'x': 1}\n```"
    assert "def foo(**kwargs):" in clean_markdown(fenced)
    assert "{**kwargs, 'x': 1}" in clean_markdown(fenced)

    prose = "Call it like foo(**kwargs) without a closing pair."
    assert "foo(**kwargs)" in clean_markdown(prose)


def test_placeholder_never_leaks_into_output_when_followed_by_a_digit():
    """A protected math span immediately followed by a literal digit (e.g.
    two separate dollar amounts on the same line) used to make the restore
    regex's \\d+ swallow that trailing digit into the placeholder's index,
    producing an out-of-range index and leaking the raw "MDSAN0_010"-shaped
    placeholder straight into the output. Fixed by wrapping placeholders in
    an unambiguous "@@...@@" boundary, matching the original production
    code's convention that got dropped when this was generalized."""
    text = "This costs $5 and that one costs $10, so**buy**the cheaper one."
    result = clean_markdown(text)
    assert "MDSAN" not in result
    assert result == "This costs $5 and that one costs $10, so **buy** the cheaper one."


def test_table_with_escaped_pipe_in_a_cell_is_preserved():
    """A cell containing a backslash-escaped pipe (valid GFM: `\\|` is a
    literal `|`, not a column boundary) used to make that row's cell count
    disagree with the header/separator row, so the incomplete-table
    remover dropped the entire otherwise-valid table."""
    text = "| A | B |\n| --- | --- |\n| a\\|b | c |\n| d | e |"
    assert clean_markdown(text) == text
