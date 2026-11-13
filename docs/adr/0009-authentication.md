# ADR-0009: GitHub sign-in for people, hashed project tokens for machines

**Status:** Accepted · **Date:** 2026-11-13

## Context

Two kinds of caller:

- **People** using the dashboard. Almost all of them have GitHub accounts, since this is a CI tool.
- **Machines:** CI pipelines uploading results, and test suites reading the quarantine list. They need long-lived credentials that are easy to put in a CI secret and easy to revoke.

Building passwords, password reset and email verification would be a lot of security-sensitive work with no product value.

## Decision

**People:** sign in with **GitHub OAuth** (Spring Security `oauth2Login`, scope `read:user`).
- Server-side session; Secure, HttpOnly, SameSite=Lax cookies; CSRF protection on state-changing session requests.
- Sessions live in memory (single instance). After a restart or host sleep, users sign in again. Acceptable for v1.

**Machines:** **project tokens**.
- Format: `fw_ing_` or `fw_read_` followed by 256 bits of randomness in base62. The prefix makes leaked tokens easy to recognise and scope obvious.
- Stored only as **SHA-256 hashes**, looked up by hash. A fast hash is safe here because tokens are random and long, unlike human passwords. Slow hashes such as bcrypt exist to protect guessable passwords.
- Each token belongs to **exactly one project** and **one scope**. Shown once, revocable, last-used time recorded (at most every 5 minutes, to avoid a write on every request).
- Wrapped in a `Secret` type whose `toString()` is redacted, plus a log filter as a second safety net.

**Access decisions:** made in one place, `ProjectAccess`, and verified by the authorization matrix test (PLAN §12.3).

## Consequences

- ➕ No password handling at all. A familiar sign-in for the target users.
- ➕ Tokens are simple for CI and safe at rest.
- ➖ Requires a GitHub account. Fine for this audience.
- ➖ In-memory sessions reset on restart. Spring Session JDBC can be added if this becomes annoying.

## Alternatives considered

- **Email and password:** much more work and risk for no product value.
- **JWT access tokens for the dashboard:** stateless, but revocation and secure browser storage are harder. Session cookies are simpler and safer for a server-rendered SPA host.
- **GitHub App installation tokens for CI:** elegant, but GitHub-only. Jenkins and others need plain tokens anyway.
