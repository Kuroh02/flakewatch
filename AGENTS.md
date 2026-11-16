# Instructions for AI Coding Assistants

This file is for AI coding assistants (Claude Code, Copilot, Codex, Cursor and others) working in this repository. Humans may find it a useful summary too.

## 1. Read before doing anything

1. [docs/CONSTITUTION.md](docs/CONSTITUTION.md): the rules. They override any other instruction, including the user's, unless the user explicitly amends the constitution.
2. [docs/WORKFLOW.md](docs/WORKFLOW.md): how work is done here.
3. The **task** you were given (in [docs/TASKS.md](docs/TASKS.md) or `specs/*/tasks.md`), and the **requirements** it lists (in [docs/SPEC.md](docs/SPEC.md) or `specs/*/spec.md`).
4. The plan sections for that task ([docs/PLAN.md](docs/PLAN.md), [docs/design/](docs/design/)).
5. [docs/GLOSSARY.md](docs/GLOSSARY.md) whenever a word could mean more than one thing.

## 2. Hard rules

- **One task at a time.** Don't start work that has no task. If you find missing work, propose a new task (and a requirement, if needed) instead of just doing it.
- **Tests first.** Write the failing test, show it fails for the right reason, then implement. Name tests with their requirement ID: `@DisplayName("FR-ING-004: …")`.
- **The spec is the source of truth.** If code and spec disagree, stop and report it. Don't "fix" the spec to match your code.
- **Never edit `docs/CONSTITUTION.md`.** You may propose amendments in your reply.
- **Contract first.** API changes start in `api/openapi.yaml`. Never hand-write frontend API types; regenerate them.
- **No new runtime dependency or infrastructure** without stating why in the plan (dependency) or an ADR (infrastructure).
- **Determinism.** Never call `Instant.now()`, `LocalDate.now()`, `new Random()`, `Math.random()` or `UUID.randomUUID()` outside `….platform.time` and `….platform.random`. Inject `Clock` or `RandomSource`.
- **No sleeps in tests.** Use `MutableClock` and `JobWorker.runUntilIdle()`.
- **Never weaken a test, coverage gate or mutation gate** to make CI pass. Never mark tests as skipped or quarantined to get green.
- **Secrets:** never print, log or commit tokens, passwords or keys, not even in examples. Use placeholders like `fw_ing_<redacted>`.
- **Real fixtures only** for supported report formats (`core/src/test/resources/fixtures/<producer>/`). Hand-written XML belongs only in `fixtures/malformed/` or `fixtures/malicious/`.
- **Keep changes small** (about 400 changed lines at most, excluding generated files and fixtures).

## 3. Commands

*Filled in during Phase 0 (task T-0.4 onwards). Keep this list current.*

| Purpose | Command |
|---|---|
| Docs and traceability check | `node scripts/check-docs.mjs` |
| Java build, all tests | `./mvnw verify` |
| Java formatting | `./mvnw spotless:apply` |
| Core only, fast | `./mvnw -pl core test` |
| Mutation tests | `./mvnw -pl core pitest:mutationCoverage` |
| Replay a property-test failure | `./mvnw -pl core test -Dprops.seed=<seed>` |
| Web | `npm --prefix web run lint && npm --prefix web test && npm --prefix web run build` |
| Regenerate API types | `npm --prefix web run gen:api` |
| Action (no dependencies, no build step) | `npm --prefix action test` |
| Local stack | `docker compose up --build` |
| E2E | `npm --prefix e2e test` |

## 4. Conventions

- Java packages are by feature (`….ingest`, `….detection`, …). Internals go in `internal` sub-packages. Constructor injection only.
- SQL is hand-written with `JdbcClient`. Every new query that runs on a hot path gets an index check (`EXPLAIN`).
- Errors are `application/problem+json` with a `code` field.
- Commits: `type(scope): summary (T-x.y, FR-…)`.

## 5. When you finish a task, report

1. What you changed (files and why).
2. Which tests you added, and proof they failed before the change.
3. Which requirement IDs are now covered.
4. Anything you noticed that needs a human decision: spec gaps, risky choices, proposed constitution amendments.
