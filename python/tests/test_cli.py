from llm_markdown_sanitizer.__main__ import main


def test_cli_rewrites_file_in_place_and_reports_it(tmp_path, capsys):
    path = tmp_path / "note.md"
    path.write_text("**Note**this needs a space", encoding="utf-8")

    exit_code = main([str(path)])

    assert exit_code == 1
    assert path.read_text(encoding="utf-8") == "**Note** this needs a space"
    assert f"fixed: {path}" in capsys.readouterr().out


def test_cli_leaves_already_clean_file_untouched(tmp_path):
    path = tmp_path / "note.md"
    path.write_text("# Title", encoding="utf-8")

    exit_code = main([str(path)])

    assert exit_code == 0
    assert path.read_text(encoding="utf-8") == "# Title"


def test_cli_with_no_args_prints_usage(capsys):
    exit_code = main([])

    assert exit_code == 2
    assert "usage" in capsys.readouterr().err
