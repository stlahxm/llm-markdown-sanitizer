"""Cross-language parity: every fixture in `fixtures/` at the repo root
must produce the same output here as it does in the Java package's
`ParityTest.java`. This is the single source of truth both languages are
checked against -- unlike the hand-written tests elsewhere in this
directory (and their Java counterparts), which can drift silently when a
fix lands in only one language. See `fixtures/README.md` for the file
format.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from llm_markdown_sanitizer import clean_markdown

_FIXTURES_DIR = Path(__file__).resolve().parents[2] / "fixtures"


def _parse_fixture(path: Path) -> tuple[str, str]:
    text = path.read_text(encoding="utf-8")
    _, rest = text.split("@@@INPUT@@@\n", 1)
    input_text, rest = rest.split("@@@EXPECTED@@@\n", 1)
    expected_text, _ = rest.split("@@@END@@@\n", 1)
    return input_text[:-1], expected_text[:-1]


def _load_fixtures() -> list[Path]:
    return sorted(_FIXTURES_DIR.glob("*.txt"))


@pytest.mark.parametrize("path", _load_fixtures(), ids=lambda p: p.name)
def test_fixture_matches_expected_output(path: Path) -> None:
    input_text, expected_text = _parse_fixture(path)
    assert clean_markdown(input_text) == expected_text
