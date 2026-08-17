# Security Policy

This library is a pure text-processing function with zero runtime dependencies and no I/O, network access, or code execution — the attack surface is intentionally minimal (regex-based string transforms only).

If you still find a security-relevant issue (e.g. a regex that's vulnerable to catastrophic backtracking / ReDoS on adversarial input), please report it privately via a GitHub security advisory ("Report a vulnerability" under the Security tab) rather than a public issue, so a fix can go out before the details are public.

We'll acknowledge reports within a few days on a best-effort basis (solo-maintained project).
