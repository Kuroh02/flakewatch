# Architecture Decision Records

An ADR records one important decision: the situation, what we chose, what it costs, and what we turned down. ADRs are never edited after they are accepted, except to change their status. If a decision changes, write a new ADR that **supersedes** the old one.

**When to write one:** the decision is hard to reverse, affects several parts of the system, or a newcomer would reasonably ask "why did you do it this way?" ([Constitution Article 9](../CONSTITUTION.md#article-9-small-steps-always-deployable-decisions-written-down)).

**How:** copy [the template](../templates/adr-template.md), use the next number, open a pull request. Status starts as *Proposed* and becomes *Accepted* when merged.

## Index

| # | Decision | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-modular-monolith.md) | One service with a pure-Java core | Accepted |
| [0003](0003-postgres-job-queue.md) | PostgreSQL table as the job queue, with leases and fencing tokens | Accepted |
| [0004](0004-evidence-based-detection-rules.md) | Evidence-based detection rules | Accepted |
| [0005](0005-property-testing-approach.md) | In-house property-testing helper instead of jqwik | Accepted (confirm in spike S-2) |
| [0006](0006-hosting-render-and-neon.md) | Host on Render and Neon free tiers | Accepted (confirm in spike S-3) |
| [0007](0007-tech-stack-baseline.md) | Java 25, Spring Boot 4, Maven, plain SQL | Accepted |
| [0008](0008-raw-files-in-postgres.md) | Keep raw uploads in PostgreSQL for 7 days | Accepted |
| [0009](0009-authentication.md) | GitHub sign-in for people, hashed project tokens for machines | Accepted |
