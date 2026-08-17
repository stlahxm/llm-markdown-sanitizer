# Security Policy

This library is a pure text-in, text-out function with zero runtime dependencies — no I/O, no network calls, no code execution. The attack surface here is about as small as it gets (it's regex-based string transforms, that's it).

That said, if you find something — say a regex that goes exponential on some adversarial input (ReDoS) — please report it privately through GitHub's "Report a vulnerability" under the Security tab instead of a public issue, so there's time to fix it before it's public.

I'll get back to you within a few days, best effort — this is a solo-maintained project.
