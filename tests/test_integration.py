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
