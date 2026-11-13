# Flakewatch

> **Is that red build real, or just a flaky test?** Flakewatch collects test results from CI, tells you which failures are **flaky** and which are **real**, and shows the **proof** for every claim.

**Status:** 📐 Planning complete, building starts at [Phase 0](docs/TASKS.md#phase-0-foundations-14-h). *(Flakewatch is a working name.)*

---

## What it does

- **Collects** JUnit XML from any CI (GitHub Action included; Jenkins and others via `curl`).
- **Detects** flaky tests from evidence: a test that failed and then passed on retry, or that passed and failed on the same commit.
- **Explains** every label: which runs, which commit, what happened.
- **Groups** similar failures, so 40 timeouts read as one problem.
- **Ranks** flaky tests by how much pain they cause.
- **Quarantines** flaky tests (JUnit and TestNG), so they stop breaking builds but keep being tracked.
- **Never breaks your build:** if Flakewatch is down, your CI behaves as if it weren't there.

## Read the docs in this order

| # | Document | What you'll learn | Time |
|---|---|---|---|
| 1 | [Constitution](docs/CONSTITUTION.md) | The rules this project never breaks | 10 min |
| 2 | [Spec](docs/SPEC.md) | What we're building and why: stories, requirements, success criteria | 20 min |
| 3 | [Detection rules](docs/design/detection-rules.md) | The heart of the product: exactly how a test gets its label | 15 min |
| 4 | [Plan](docs/PLAN.md) | How it's built: architecture, designs, testing, CI/CD, deployment | 45 min |
| 5 | [Tasks](docs/TASKS.md) | The ordered checklist to build v1 | 10 min |
| 6 | [Workflow](docs/WORKFLOW.md) | How work moves from idea to shipped code | 10 min |

**Reference:**
[Glossary](docs/GLOSSARY.md) ·
[Data model](docs/design/data-model.md) ·
[API contract](api/openapi.yaml) ·
[Decision records](docs/adr/README.md) ·
[Templates](docs/templates/) ·
[First post-v1 feature](specs/001-ci-feedback/spec.md) ·
[Rules for AI assistants](AGENTS.md)

## Milestones

| Milestone | What's included | Phases |
|---|---|---|
| **M1: Live demo** | Upload, detection, failure groups, read-only dashboard, GitHub Action, live demo data | 0–4 |
| **M2: v1.0** | GitHub sign-in, projects and tokens, settings, quarantine (JUnit and TestNG) | 5–6 |
| **M3: v1.1** | Measured performance, faster cold start, PR comments and alerts, retrospective | 7 |

## Tech at a glance

Java 25 · Spring Boot 4 · PostgreSQL 17 · React 19 + TypeScript · Playwright · GitHub Actions · Docker · Render + Neon (free tiers)

## Planned repository layout

```
core/          pure-Java logic: parsing, detection, fingerprints
server/        Spring Boot service (API, jobs, persistence; serves the web app)
quarantine/    JUnit and TestNG quarantine libraries
web/           React dashboard
action/        GitHub Action uploader
e2e/  perf/    end-to-end + accessibility tests; load tests
api/           OpenAPI contract
docs/  specs/  constitution, spec, plan, tasks, design, ADRs, feature specs
```

## Running it locally

*Available after Phase 0.* The goal (SC-02) is:

```bash
git clone https://github.com/<you>/flakewatch && cd flakewatch
cp .env.example .env
docker compose up
# open http://localhost:8080
```

## Checking the docs

```bash
node scripts/check-docs.mjs            # requirements ↔ tasks, IDs, links (runs in CI)
node scripts/check-docs.mjs --strict   # also requires a test for every M1/M2 requirement (from M2)
```

## License

MIT
