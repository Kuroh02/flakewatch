# Flakewatch Constitution

**Version:** 1.0.0 · **Ratified:** 2026-11-13 · **Last amended:** 2026-11-13

This file holds the rules this project never breaks. Every spec, plan, task and pull request is checked against it.

If a rule here gets in the way, don't quietly work around it. Change the rule in the open, using the process at the bottom of this file.

---

## How to read this

The constitution has two parts:

- **Part A: Product principles.** What the product promises its users.
- **Part B: Engineering articles.** How we build it.

Each rule has three parts:

- **Rule:** what we always or never do.
- **Why:** the reason, so the rule can be applied with judgement.
- **How we check:** the test, tool or review step that catches a violation.

Words like *test case*, *execution*, *evidence* and *fail-open* are defined in [GLOSSARY.md](GLOSSARY.md).

---

## Part A: Product principles

### P1. Evidence over verdicts

**Rule:** Never label a test *Flaky* without stored proof that the user can see. If we only have a hunch, we say *Suspected*, not *Flaky*.

**Why:** Teams already guess which tests are flaky. What they lack is proof. A tool that over-claims loses trust fast, and then nobody acts on it.

**How we check:**
- Every Flaky status in the database has at least one evidence item ([FR-DET-007](SPEC.md#detection-fr-det)).
- A property test asserts that no input without evidence produces *Flaky* ([detection rules](design/detection-rules.md#7-properties-that-must-always-hold)).
- UI review: every status badge links to the reason behind it.

### P2. Never break the user's build

**Rule:** Anything we ship into a user's pipeline (the GitHub Action, the quarantine libraries) must *fail open*. If Flakewatch is slow, down or misconfigured, the user's build behaves exactly as if Flakewatch didn't exist.

**Why:** A tool meant to reduce CI noise must never become a new source of CI failures.

**How we check:**
- Each client has a test where the server is unreachable, slow, or returns errors, and the build outcome is unchanged.
- Failing the build on upload errors is possible only through an explicit opt-in setting (`fail-on-error: true`).

### P3. Signal over noise

**Rule:** Alert on *changes* (a test became flaky, a test broke), never on the same problem again and again. Rank lists by impact so the most expensive problem is first.

**Why:** People ignore noisy tools. The product exists to save attention, not spend it.

**How we check:** Notification rules fire only on status-change events, and a test asserts that a repeated identical upload produces no new events.

---

## Part B: Engineering articles

### Article 1. Spec first, and everything is traceable

**Rule:**
- Behaviour changes start with a written requirement that has an ID (for example `FR-ING-004`).
- Code, tests and tasks point back to those IDs.
- Small chores (typos, dependency bumps, formatting) don't need a requirement.

**Why:** Writing down *what* and *why* before *how* catches misunderstandings while they are cheap to fix. IDs let anyone follow a line from "why does this exist?" to "where is it tested?".

**How we check:**
- `scripts/check-docs.mjs` fails CI when an M1 or M2 requirement has no task, or a task points to a requirement that doesn't exist.
- Strict mode, switched on once tests exist, also fails when a requirement has no test.
- The PR template asks for the task and requirement IDs.

### Article 2. Tests come first, and they are part of the feature

**Rule:**
- New behaviour starts with a failing test.
- Every bug fix starts with a test that reproduces the bug.
- A feature without tests is not done, however well it seems to work.
- Domain rules (parsing, detection, fingerprinting) also get **property-based tests**: tests that check a rule holds across hundreds of generated inputs.

**Why:** A test written first proves it can fail, so it really checks something. Property tests find the edge cases nobody thought to write down.

**How we check:**
- CI gates: server line coverage ≥ 80%; mutation score ≥ 75% on the `core` module ([NFR-QUAL-001](SPEC.md#quality-nfr-qual)).
- Reviewers look for the test that failed before the fix.

### Article 3. Data correctness is non-negotiable

**Rule:** These invariants hold at all times:

1. An upload we accepted (`202`) is never silently lost. It is processed, or marked failed with a reason.
2. Processing the same data twice never double-counts anything.
3. The same set of results always produces the same statuses, whatever order they arrived in.
4. A token for one project can never read or write another project's data.

**Why:** If the numbers are wrong once, nobody trusts the dashboard again.

**How we check:**
- Each invariant has a named automated test: idempotency tests, concurrency tests with several workers, an order-independence property test, and the authorization matrix test.
- These tests are never skipped or quarantined.

### Article 4. Simple by default

**Rule:**
- One deployable service, one PostgreSQL database.
- No extra infrastructure (message broker, cache, search engine, second service) unless an ADR shows that PostgreSQL or plain code can't meet a stated requirement.
- Don't build for problems we don't have yet.

**Why:** Every moving part costs time to build, test, deploy, monitor and explain. For this project's scale, one well-built service beats five half-built ones.

**How we check:**
- The "Constitution check" table in every plan.
- New runtime dependencies (libraries that ship in the app, not test-only tools) need a line in the plan explaining why.
- New infrastructure needs an ADR.

### Article 5. The API contract comes first

**Rule:**
- [`api/openapi.yaml`](../api/openapi.yaml) is the source of truth for the HTTP API.
- Change the contract first, then the code.
- The frontend's API types are generated from the contract, never hand-written.

**Why:** A written contract lets the server, the web app and the CI clients evolve safely. It is also the API's documentation, so it can't drift out of date.

**How we check:**
- API tests validate every request and response against the contract.
- CI fails if the generated frontend types are out of date or the contract fails linting.

### Article 6. Secure by default

**Rule:**
- Least privilege everywhere. Tokens are per project and per scope, stored only as hashes and shown once.
- All input is untrusted. XML parsing has DTDs and external entities switched off, and every size, count and depth limit is enforced.
- Every API operation has authorization tests for every type of caller.
- Secrets live only in environment variables. They never appear in code, git history or logs.

**Why:** This service accepts files from strangers' CI pipelines. Treat every upload as hostile.

**How we check:**
- The authorization matrix test fails if any API operation lacks an entry.
- Malicious-input tests (XXE, entity expansion, zip bombs, oversize files) run in CI.
- CodeQL, dependency scanning and container scanning block merges on critical findings.

### Article 7. Deterministic and reproducible

**Rule:**
- No code reads the system clock or creates random values directly. Time comes from an injected `Clock`, and randomness from an injected, seedable source.
- Builds use pinned versions: lockfiles, the Maven wrapper, and GitHub Actions pinned by commit SHA.
- **Our own test suite has zero known flaky tests.** A flaky test of ours is a top-priority bug.

**Why:** A flaky-test detector with flaky tests has no credibility. Determinism also makes every bug reproducible.

**How we check:**
- ArchUnit rules ban `Instant.now()`, `LocalDate.now()`, `new Random()` and similar calls outside one configuration class.
- Property tests print their seed on failure so the failure can be replayed.
- Our CI uploads its own results to Flakewatch (dogfooding), so our flaky tests show up on our own dashboard.

### Article 8. Observable and operable

**Rule:**
- Every background path (jobs, retries, purges) logs structured events and exposes metrics.
- Liveness checks never touch the database; readiness checks do.
- Every operation someone might need at 2 a.m. (rollback, reprocess, rotate a secret) is written down in the runbook.

**Why:** Background work fails silently unless we make it loud. You can't fix what you can't see.

**How we check:**
- Review checklist item: "What log line and metric show this working or failing?"
- The runbook is part of the Definition of Done for operational features.

### Article 9. Small steps, always deployable, decisions written down

**Rule:**
- Work in small pull requests (aim for under about 400 changed lines, excluding generated files and fixtures).
- `main` is always green and deployable. Every merge to `main` deploys automatically.
- Any decision that is hard to reverse, or that a newcomer would question, gets an Architecture Decision Record (ADR) in [`docs/adr/`](adr/).

**Why:** Small changes are easy to review, test and roll back. ADRs stop old debates from being re-argued and show *how* decisions were made.

**How we check:**
- Branch protection: pull requests, required status checks, linear history.
- The deploy pipeline runs a smoke test after every deploy.
- Plans list the ADRs they create or rely on.

---

## Quality gates (summary)

These numbers come from the articles above and are enforced in CI.

| Gate | Threshold | Enforced by |
|---|---|---|
| Formatting and lint | No violations | Spotless, ESLint, Prettier |
| Server line coverage | ≥ 80% | JaCoCo |
| Mutation score, `core` module | ≥ 75% | PIT |
| Architecture rules | No violations | ArchUnit |
| API contract | Lint clean; all API tests validate against it | Redocly lint, OpenAPI validation filter |
| Accessibility, core pages | No *serious* or *critical* axe violations | Playwright + axe |
| Security | No critical findings | CodeQL, Dependabot, Trivy |
| Traceability | Every M1 and M2 requirement has a task, and later a test | `scripts/check-docs.mjs` |

---

## Exceptions

Sometimes breaking a rule is the right call. When that happens:

1. Write it in the plan's **Complexity and exceptions** section: which rule, why, and when it will be removed.
2. Link it from the pull request.
3. Add a task to remove the exception, or explain why it is permanent.

An exception without a written reason is a violation.

---

## Amending this constitution

1. Open a pull request that changes this file, with an ADR that explains the change.
2. Bump the version:
   - **Major:** a rule is removed or reversed.
   - **Minor:** a rule is added.
   - **Patch:** wording only.
3. Update templates and checklists that quote the changed rule.
4. Add a line to the amendment log below.

AI coding assistants may *suggest* amendments but must never change this file on their own. See [AGENTS.md](../AGENTS.md).

### Amendment log

| Version | Date | Change |
|---|---|---|
| 1.0.0 | 2026-11-13 | First version. |
