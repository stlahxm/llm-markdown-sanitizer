# Cross-language parity fixtures

Each `.txt` file here is one input/output pair that **both** the Python
and Java packages must produce identically. This is the single source of
truth for "does the Java port still behave like the Python one" -- unlike
`python/tests/` and `java/src/test/`, which are hand-written per language
and can silently drift (see `demo/strikethrough-spacing-python-only`
branch for a real example of that happening).

## Format

```
@@@INPUT@@@
<raw input text, verbatim, no trailing newline stripped>
@@@EXPECTED@@@
<raw expected output text, verbatim>
@@@END@@@
```

Both `python/tests/test_parity.py` and
`java/src/test/.../ParityTest.java` load every `*.txt` file in this
directory and assert `clean(input) == expected`. Adding a fixture here
without updating both language implementations is exactly the failure
this harness exists to catch.
