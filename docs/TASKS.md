# Tasks: Flakewatch v1

**Follows:** [PLAN.md](PLAN.md) v1.0 · **Requirements:** [SPEC.md](SPEC.md) v1.0

This is the single, ordered checklist for building v1. Work top to bottom.

## How to use this file

- **One task ≈ half a day or less.** If a task grows bigger, split it here before starting.
- **Tick the box** in the same pull request that finishes the task.
- **IDs after the dash** are the requirements the task serves. Use them in test names, commits and pull requests (see [WORKFLOW.md](WORKFLOW.md#4-the-daily-loop-one-task)).
- **"Tests first"** means: write the tests, watch them fail for the right reason, then write the code.
- **Each phase ends with exit criteria.** Don't start the next phase until they are all true. A phase that is "almost done" is not done.
- **Found missing work?** Add a task here first, with its requirement IDs. If no requirement covers it, the spec has a gap: update [SPEC.md](SPEC.md) first.

Before starting a phase, re-read the spec sections and plan sections it lists.

---

## Phase 0: Foundations (~14 h)

**Goal:** an empty but real app goes from a commit to a live URL through a green pipeline. The API contract is reviewed, and the risky unknowns are answered.
**Read first:** PLAN §2, §5, §6.3, §19, §21, §23 · ADR-0002, 0006, 0007

### Repository and governance

- [x] **T-0.1** Create the `flakewatch` GitHub repository. Add this docs pack, `LICENSE` (MIT), `.gitignore`, `.editorconfig` and `CODEOWNERS`.
- [ ] **T-0.2** Repository settings: protect `main` (pull requests required, linear history, no force pushes). Add `.github/dependabot.yml` for Maven, npm and GitHub Actions. Turn on secret scanning with push protection, and CodeQL default setup. — NFR-SEC-005
- [ ] **T-0.3** Add a CI `docs` job running `node scripts/check-docs.mjs`, plus Redocly lint for `api/openapi.yaml`. — NFR-QUAL-003

### Walking skeleton

- [ ] **T-0.4** Maven parent POM: Java 25, Spring Boot 4.x BOM, pinned plugin versions, Maven Wrapper, modules `core` and `server`. Configure Spotless (formatting) and JaCoCo (coverage). `./mvnw verify` passes. — NFR-QUAL-001
- [ ] **T-0.5** `server`: a Spring Boot app with the webmvc, actuator, validation, security, JDBC and Flyway starters plus the PostgreSQL driver. Liveness health group without the database; readiness with it. `GET /api/v1/version` from build info. — NFR-OBS-003, NFR-OPS-001
- [ ] **T-0.6** Tests first: `NoDbOnLivenessIT` (pause the database container → liveness `UP`, readiness `DOWN`). Then make it pass. — NFR-OBS-003
- [ ] **T-0.7** `platform` package: `Clock` and `RandomSource` beans; a `MutableClock` for tests. First ArchUnit rules: `core` independence, the clock and randomness bans, no field injection. — NFR-QUAL-002
- [ ] **T-0.8** Testcontainers base class: one shared PostgreSQL 17 container with `@ServiceConnection`, and a JUnit extension that truncates tables after each test. Flyway `V1` (users, projects, api_tokens) runs in an integration test.
- [ ] **T-0.9** Structured JSON logging; an `X-Request-Id` filter that puts the ID in the MDC; a problem-details error handler skeleton. — NFR-OBS-001
- [ ] **T-0.10** `web/`: Vite + React + TypeScript (strict), ESLint, Prettier, Vitest. One placeholder page that shows `/api/v1/version`. Vite dev-server proxy for `/api`.
- [ ] **T-0.11** Multi-stage `Dockerfile` (non-root, layered jar, `HEALTHCHECK`), `compose.yaml` (app + PostgreSQL 17) and `.env.example`. Check that the README quickstart works from a fresh clone. — NFR-OPS-001
- [ ] **T-0.12** `ci.yml` with jobs `docs`, `java`, `web` and `image` (build + Trivy scan). Add caching, minimal `permissions:`, actions pinned by SHA, and cancel-in-progress for the same branch. — NFR-SEC-005, NFR-OPS-002
- [ ] **T-0.13** Production: create the Neon project and the Render service (PLAN §21.3). `deploy.yml`: push `ghcr.io/<you>/flakewatch:<sha>` → trigger Render → wait until `/api/v1/version` returns the SHA → smoke test → roll back to the previous SHA on failure. — NFR-OPS-002

### Contract and spikes

- [ ] **T-0.14** Review `api/openapi.yaml` against SPEC.md, line by line. Fix gaps. Lint clean. — NFR-QUAL-003
- [ ] **T-0.15** Spike S-1: build the sample projects in `tools/fixture-gen/` (Maven Surefire with `rerunFailingTestsCount`, Gradle with test-retry, TestNG, Playwright, WebdriverIO, jest-junit). Each has passing, failing, erroring, skipped, flaky and parameterised tests. Commit the generated XML to `core/src/test/resources/fixtures/<producer>/`. — FR-ING-006, FR-ING-007
- [ ] **T-0.16** Spike S-2: write the `Props` helper (generators, seeds, list shrinking) with its own tests. Record the outcome in ADR-0005. — NFR-QUAL-001
- [ ] **T-0.17** Spike S-3: run the image with `docker run -m 512m --cpus 0.1`. Record heap, memory use and startup time; tune `JAVA_TOOL_OPTIONS`. Record in ADR-0006. — NFR-PERF-004
- [ ] **T-0.18** Spike S-5: run PIT on `core` with one trivial test. Record the result in ADR-0007, or apply the fallback. — NFR-QUAL-001
- [ ] **T-0.19** Spike S-6: RestAssured with the OpenAPI validation filter against `/api/v1/version`. Record the result, or apply the fallback. — NFR-QUAL-003

### Exit criteria

- [ ] The live URL returns the deployed commit SHA from `/api/v1/version`.
- [ ] CI is green with all jobs required by branch protection.
- [ ] Spikes S-1, S-2, S-3, S-5 and S-6 are recorded, and fixture files are committed.

---

## Phase 1: Ingestion (~26 h)

**Goal:** CI results can be uploaded, and they are stored correctly, exactly once.
**Read first:** SPEC §6 (FR-ING), §8 · PLAN §7, §8, §15 · data-model.md · ADR-0003, 0008, 0009

### Schema, tokens, access

- [ ] **T-1.1** Flyway `V2` (runs, uploads, files, jobs) and `V3` (test cases, failure groups, executions, daily stats, `pg_trgm`), as in data-model.md. Add a migration integration test. — FR-ING-010
- [ ] **T-1.2** Tests first: token generation (prefix, 256-bit random, base62), hashing, lookup, revoked → `401`, wrong scope → `403`, `Secret` redacted in logs. Then implement `TokenService` and `TokenAuthenticationFilter`. — NFR-SEC-001, FR-PRJ-005
- [ ] **T-1.3** Bootstrap command (`--flakewatch.bootstrap.project=…`): creates a project, prints an ingest token once, and exits. Add an integration test. — FR-PRJ-007
- [ ] **T-1.4** `ProjectAccess`: the single place that decides access. Public/private read rules, and `404` (not `403`) for private projects. Unit tests. — FR-PRJ-004

### Parser (`core`)

- [ ] **T-1.5** Tests first: malicious fixtures (XXE, entity expansion, external DTD, 10,000-level nesting, a 1 MB attribute) are all rejected or limited safely. Then build the hardened StAX factory and limits. — NFR-SEC-002
- [ ] **T-1.6** Approval-test harness: each fixture → parse → JSON → compare with `<name>.expected.json`. Write each expected file by reviewing the output carefully, not by copying it blindly. — FR-ING-006
- [ ] **T-1.7** Implement the streaming `ReportParser`: `testsuites`, `testsuite`, `testcase`, `failure`, `error`, `skipped`, time parsing, `classname` fallback, warnings. Make the Surefire, Gradle, TestNG and Jest fixtures pass. — FR-ING-006, FR-ING-009, FR-ING-010
- [ ] **T-1.8** Retries: `flakyFailure`, `flakyError`, `rerunFailure`, `rerunError` and repeated entries, with correct execution indexes. — FR-ING-007
- [ ] **T-1.9** Make the Playwright and WebdriverIO fixtures pass (JavaScript stack traces, file-path class names). — FR-ING-006
- [ ] **T-1.10** `TestKeys`: test key and method key rules, with a 2,000-character cap plus hash suffix. Property tests: normalising is idempotent; keys are deterministic; the method key is a prefix of the test key. — FR-ING-010
- [ ] **T-1.11** Property tests `ParserNeverExplodesProperties` (random bytes, mutated fixtures) and a round trip (`Gens.junitXml()` → parse → same model). — NFR-SEC-002, FR-ING-009

### Upload API

- [ ] **T-1.12** Tests first (`@WebMvcTest` + contract validation): metadata errors list every field; too many files; size limits → `413`; wrong root element → `400 not-a-junit-report`; gzip handling; zip-bomb limit. — FR-ING-001, FR-ING-002, FR-ING-008, NFR-SEC-002
- [ ] **T-1.13** Implement the upload controller: validation, streaming limits, root-element check, content hash. — FR-ING-001, FR-ING-002, FR-ING-008
- [ ] **T-1.14** Tests first: `IdempotentUploadIT` (the same upload 3 times; 8 threads at once; different content → `409`). Then implement the accept transaction: upsert run, insert upload, files and job. — FR-ING-003, FR-ING-004, NFR-REL-001
- [ ] **T-1.15** Group uploads across shards and attempts into runs; return `409 run-metadata-mismatch` on conflicting metadata. — FR-ING-011
- [ ] **T-1.16** Upload status endpoint `GET /uploads/{id}`, validated against the contract. — FR-ING-005

### Job queue

- [ ] **T-1.17** Tests first: `QueueConcurrencyIT`, `LeaseTakeoverIT` (with `MutableClock`), `PoisonPillIT`, and a unit test for backoff with seeded jitter. — NFR-REL-001, NFR-REL-002
- [ ] **T-1.18** Implement `JobRepository` (enqueue with dedupe, claim, complete with fencing, fail with backoff, dead) and `JobWorker` (thread pool, plus `runUntilIdle()` for tests). — NFR-REL-001
- [ ] **T-1.19** Wake-up strategy: signal after commit, a next-due timer capped at 10 minutes, and a claim at startup. Test that an idle worker sends no SQL for 10 simulated minutes, by counting statements through a datasource proxy. — NFR-COST-001
- [ ] **T-1.20** Graceful shutdown and lease release; `ShutdownReleasesLeaseIT`. — NFR-REL-003

### Processing

- [ ] **T-1.21** Tests first: `ReprocessIsHarmlessIT`. Then `ProcessUploadHandler`: batches of 1,000, upsert test cases, insert executions with `on conflict do nothing`, daily stats from inserted rows only, upload `PROCESSED` with summary and warnings, and `DEAD` → upload `FAILED` with a reason. — FR-ING-005, FR-ING-010, NFR-REL-002
- [ ] **T-1.22** Trim failure details (4 KB message, 16 KB stack trace), and store them only for failures. — FR-ING-010, NFR-COST-001
- [ ] **T-1.23** Retention jobs `PURGE_RAW_FILES` (7 days) and `PURGE_OLD_DATA` (90 / 400 / 7 / 30 days), with daily dedupe keys. Integration tests move the `MutableClock`. — FR-ING-012, NFR-DATA-001
- [ ] **T-1.24** Metrics: uploads counter, upload bytes, job timer, pending gauge, oldest-pending age. Test that they are registered and change. — NFR-OBS-002
- [ ] **T-1.25** Performance check: an integration test processes a generated 10,000-test upload and logs the time, failing above 15 s on CI. Measure accept latency with `perf/upload.js` (k6). — NFR-PERF-001, NFR-PERF-002
- [ ] **T-1.26** Write `docs/guides/upload-from-any-ci.md` with a `curl` example, and try it against production. — FR-CI-006

### Exit criteria

- [ ] All FR-ING M1 tests and the NFR-REL tests are green.
- [ ] A real Surefire report uploaded from your laptop to production shows `PROCESSED` with correct counts.
- [ ] Sending the same report again creates no duplicates.

---

## Phase 2: Detection and grouping (~20 h)

**Goal:** every test has a correct status backed by evidence, and failures are grouped.
**Read first:** [detection-rules.md](design/detection-rules.md) (all of it) · SPEC §6 (FR-DET, FR-GRP) · PLAN §9, §10 · ADR-0004, 0005

### Classifier (`core`)

- [ ] **T-2.1** Turn worked examples E01–E17 into `DetectionExamplesTest`. All fail at first. — FR-DET-001, FR-DET-002, FR-DET-003, FR-DET-004, FR-DET-005
- [ ] **T-2.2** History generators (`Gens.histories()` with variants, branches, commits, retries, skips and times). Write PROP-DET-01 to PROP-DET-11 as property tests (failing). — FR-DET-006
- [ ] **T-2.3** Implement data preparation: window, dedupe, total ordering, variant split, final outcomes. — FR-DET-006
- [ ] **T-2.4** Implement evidence (`RETRY_PASS`, `SAME_COMMIT_MIXED`) with explanations, keeping the 5 most recent. — FR-DET-002, FR-DET-003, FR-DET-007
- [ ] **T-2.5** Implement rules 1–6 with settings, and combine variants in attention order. — FR-DET-001, FR-DET-004, FR-DET-005
- [ ] **T-2.6** Impact and flip rate. — FR-DET-012
- [ ] **T-2.7** Run PIT on `core` and raise the mutation score to ≥ 75%. Add the CI `mutation` job. — NFR-QUAL-001

### Fingerprinting (`core`)

- [ ] **T-2.8** Tests first: PROP-FP-01 to PROP-FP-05 and an example table (Java and JavaScript stacks). Then `MessageNormalizer`, `FrameFilter` and `Fingerprinter`. — FR-GRP-001, FR-GRP-002
- [ ] **T-2.9** Wire fingerprinting into `ProcessUploadHandler`: upsert `failure_groups`, and count occurrences from inserted rows only. Integration test. — FR-GRP-001, NFR-REL-002

### Status persistence

- [ ] **T-2.10** Flyway `V4`. `ReclassifyTestsHandler`: load window executions per batch, classify, upsert statuses, insert events on change. Integration test: upload examples E01–E17 as real uploads and check the stored statuses. — FR-DET-008, FR-DET-009
- [ ] **T-2.11** The reclassification selection rule (detection-rules §11). Integration test showing that skipped tests still end up with the correct status (backs PROP-DET-08). — FR-DET-008, NFR-PERF-002
- [ ] **T-2.12** `RECLASSIFY_AGING` daily job and `project_daily_snapshots`. Integration test: move the clock 15 days and the evidence expires. — FR-DET-010
- [ ] **T-2.13** Detection settings model: defaults, range validation, stored in `projects.settings`, plus the `RECLASSIFY_PROJECT` job. (The API and UI come in Phase 5.) — FR-DET-011

### Read APIs for the dashboard

- [ ] **T-2.14** Tests list API: status filter, trigram search, sort by impact, recent or name, cursor pagination. Contract tests. — FR-UI-002
- [ ] **T-2.15** Test detail and executions APIs: variants, evidence, 50-point timeline, failure groups, duration trend. — FR-UI-003, FR-DET-007
- [ ] **T-2.16** Runs list and detail APIs, with failure labels (known flaky, new failure, broken, passed on retry). — FR-UI-004
- [ ] **T-2.17** Failure groups list and detail APIs. — FR-GRP-003, FR-UI-005
- [ ] **T-2.18** Project summary API: counts, 30-day trend from snapshots, recent runs. — FR-UI-001
- [ ] **T-2.19** `AuthorizationMatrixIT` skeleton: reads every operation from `openapi.yaml` and fails if one has no matrix entry. Fill in entries for the upload and read endpoints. — NFR-SEC-003, FR-PRJ-004, FR-PRJ-005

### Exit criteria

- [ ] E01–E17 and all PROP-DET and PROP-FP properties pass; mutation score ≥ 75%.
- [ ] Statuses in production match what you expect from your uploaded test data.
- [ ] All read endpoints pass contract and authorization tests.

---

## Phase 3: Dashboard, read-only (~20 h)

**Goal:** anyone can browse a public project's statuses, evidence, runs and failure groups.
**Read first:** SPEC US-02 to US-05, US-09, FR-UI, NFR-A11Y · PLAN §13

- [ ] **T-3.1** Generate TypeScript types from `openapi.yaml` (openapi-typescript) and a typed client (openapi-fetch). CI fails if the generated types are out of date. — NFR-QUAL-003
- [ ] **T-3.2** App shell: routing, layout, header, error boundary, shared loading/empty/error components, and a URL-state hook with tests. — FR-UI-007
- [ ] **T-3.3** Components: status badge, timeline strip and sparkline, using icon + text + shape, with a hidden data table. Component tests check accessible roles and names. — NFR-A11Y-001, FR-UI-003
- [ ] **T-3.4** Home page: list of public projects. — FR-UI-001
- [ ] **T-3.5** Project overview page. — FR-UI-001
- [ ] **T-3.6** Tests list page: filters, debounced search, sort, pagination, all in the URL. — FR-UI-002, FR-UI-007
- [ ] **T-3.7** Test detail page: variants, evidence with run links, timeline, failure groups, durations. — FR-UI-003, FR-DET-007
- [ ] **T-3.8** Runs list and run detail pages, with labelled failures. — FR-UI-004
- [ ] **T-3.9** Failure group pages. — FR-UI-005
- [ ] **T-3.10** Dark mode, a responsive layout that works at 360 px wide, visible focus styles, reduced motion. — NFR-A11Y-001
- [ ] **T-3.11** End-to-end setup in `e2e/`: start the compose stack, seed it by uploading fixtures through the API, and write Playwright journeys for US-02, US-03, US-04, US-05 and US-09. Run an axe scan on every page, failing on serious or critical issues. Add the CI `e2e` job. — NFR-A11Y-001, FR-UI-007
- [ ] **T-3.12** XSS check: a test named `<img src=x onerror=alert(1)>` renders as plain text. Add a lint rule banning `dangerouslySetInnerHTML`. — NFR-SEC-006
- [ ] **T-3.13** Serve the SPA from Spring: static files, history fallback for client routes, long cache headers for hashed assets. — NFR-OPS-001

### Exit criteria

- [ ] The live dashboard is browsable for a project with real uploaded data.
- [ ] E2E journeys and axe checks are green in CI.

---

## Phase 4: CI integration and live demo → M1 (~14 h)

**Goal:** real teams (and the demo repository) can send results with one CI step, and the live link stays full of real data.
**Read first:** SPEC US-01, FR-CI · PLAN §14, §21.4, §21.5 · ADR-0006

- [ ] **T-4.1** Action skeleton in `action/`: TypeScript, `action.yml` with `runs.using: node24`, a bundler, Vitest. CI job that fails if `dist/` is out of date. — FR-CI-001
- [ ] **T-4.2** Tests first: metadata from the environment for push, pull request and re-run (attempt 2) events; missing token → skip notice; no files → warning. Then implement. — FR-CI-001, FR-CI-004
- [ ] **T-4.3** Tests first, against a local HTTP test server: retries on network errors, `429` and `5xx`, with backoff and `Retry-After`; the time budget; fail-open versus `fail-on-error`; token masking. Then implement. — FR-CI-002, FR-CI-003
- [ ] **T-4.4** Job summary with counts and the run link. — FR-CI-005
- [ ] **T-4.5** Dogfooding: this repository's `ci.yml` uses `./action` to upload its own results (with Surefire `rerunFailingTestsCount=1`) to the `flakewatch-self` project. — NFR-QUAL-004
- [ ] **T-4.6** Release `action-v1.0.0` and a moving `action-v1` tag. Write the action's README. — FR-CI-001
- [ ] **T-4.7** Add a `Jenkinsfile` example to the upload guide, and try it with a local Jenkins container. — FR-CI-006
- [ ] **T-4.8** Create the demo repository `flakewatch-demo` (PLAN §21.5): the test set, a schedule every 3 hours, two variants, a keep-alive workflow, uploads to the `demo` project. — FR-DET-002, FR-DET-004, FR-DET-005
- [ ] **T-4.9** Rate limits with Bucket4j: uploads per token, public reads per IP, `429` with `Retry-After`. Tests. — NFR-SEC-004
- [ ] **T-4.10** Production hardening: Hikari idle settings, JVM flags, baseline security headers (CSP, `X-Content-Type-Options`, `Referrer-Policy`), and `synthetic.yml`. — NFR-COST-001, NFR-PERF-004, NFR-OBS-002
- [ ] **T-4.11** Write `docs/RUNBOOK.md`: deploy, roll back, rotate tokens and secrets, reprocess `DEAD` jobs, inspect the queue, notes on restoring the database. — NFR-OPS-002
- [ ] **T-4.12** README: live link, screenshots or a GIF, "how it works", the architecture diagram, local quickstart. Tag `v0.1.0`. — NFR-OPS-001

### Exit criteria (M1: Live demo)

- [ ] Demo data has been flowing for at least 48 hours, and SC-06 holds on the demo project.
- [ ] Flakewatch's own CI results appear on its own dashboard.
- [ ] `node scripts/check-docs.mjs` passes. `v0.1.0` is released.

---

## Phase 5: Accounts and projects (~14 h)

**Goal:** people can sign in, create projects and manage tokens and settings themselves.
**Read first:** SPEC US-06, FR-PRJ, NFR-SEC-006 · PLAN §12.3, §15 · ADR-0009

- [ ] **T-5.1** GitHub OAuth app (development and production). Spring Security `oauth2Login`; create or update the user on sign-in; `/me`; logout. Tests with a mocked OAuth user. — FR-PRJ-001
- [ ] **T-5.2** Session hardening: cookie flags, CSRF (cookie-to-header) for session writes, final HSTS and CSP. Tests. — NFR-SEC-006
- [ ] **T-5.3** Projects API: create (slug rules, `slug-taken`), update, delete (cascading). Owner only. Contract tests. — FR-PRJ-002, FR-PRJ-006
- [ ] **T-5.4** Tokens API: create (shown once, `Cache-Control: no-store`), list prefixes, revoke. Throttled last-used updates. — FR-PRJ-003, NFR-SEC-001
- [ ] **T-5.5** Detection settings through `PATCH /projects/{slug}` (validated ranges) → queues `RECLASSIFY_PROJECT`. — FR-DET-011
- [ ] **T-5.6** Complete `AuthorizationMatrixIT`: every operation × 7 caller types. — NFR-SEC-003
- [ ] **T-5.7** UI: sign in and out, "my projects", create-project page. — FR-PRJ-001, FR-PRJ-002
- [ ] **T-5.8** UI settings page: tokens (copy-once dialog), detection settings, visibility, delete with typed confirmation. — FR-UI-006
- [ ] **T-5.9** E2E (US-06), using an OAuth stub in an `e2e` profile: sign in, create project, create token, upload with it, revoke it → `401`. — FR-PRJ-003
- [ ] **T-5.10** Command to assign an owner to bootstrap projects. Restrict bootstrap to operator use. — FR-PRJ-007

### Exit criteria

- [ ] All US-06 scenarios pass end to end.
- [ ] The authorization matrix covers every operation in the contract.

---

## Phase 6: Quarantine → M2 (~18 h)

**Goal:** teams can quarantine flaky tests so they stop breaking builds, while Flakewatch keeps tracking them.
**Read first:** SPEC US-07, US-08, FR-QUA, FR-ING-013 · PLAN §11 · glossary "Quarantine"

- [ ] **T-6.1** Flyway `V5` and the quarantine API: create, release, list with `ETag`/`304`, `409 already-quarantined`. Contract and matrix tests. — FR-QUA-001, FR-QUA-002
- [ ] **T-6.2** Mark executions as quarantined at ingest. Parse the `[flakewatch:quarantined-failure]` marker → `FAILED` + quarantined. Add fixtures. — FR-ING-013
- [ ] **T-6.3** Ready-to-release (20 passes in a row across ≥ 3 days), computed during reclassification. Tests with `MutableClock`. — FR-QUA-006
- [ ] **T-6.4** Spike S-4: do method keys match, and does TestNG's result rewrite work reliably? Record the results. — FR-QUA-003, FR-QUA-004
- [ ] **T-6.5** `quarantine-client`: environment config, one lazy fetch per JVM, 3 s timeout, ETag cache file, fail-open. Tests against a stub server that is unreachable, slow, returns `500`, or returns bad JSON. — FR-QUA-005
- [ ] **T-6.6** `quarantine-junit`: MUTE and SKIP modes (exception handler and execution condition). `EngineTestKit` tests, run against JUnit Jupiter 5.10, 5.14 and 6.x. — FR-QUA-003
- [ ] **T-6.7** `quarantine-testng`: MUTE and SKIP modes (invoked-method listener). Programmatic TestNG tests on TestNG 7.10+. — FR-QUA-004
- [ ] **T-6.8** Decide Q-02 (Maven Central or GitHub Packages), publish the libraries, and write the usage docs. — FR-QUA-003, FR-QUA-004
- [ ] **T-6.9** UI: quarantine button with reason on the test page, "Quarantined" badge, quarantine list page with ready-to-release hints, release action. — FR-UI-008
- [ ] **T-6.10** The demo repository adopts the JUnit library in MUTE mode. Verify US-07 end to end. — FR-QUA-003
- [ ] **T-6.11** E2E tests for US-07 and US-08. — FR-QUA-001, FR-QUA-006
- [ ] **T-6.12** Release `v1.0.0`: CHANGELOG, release notes, ADR review. `node scripts/check-docs.mjs --strict` passes. — NFR-QUAL-003

### Exit criteria (M2: v1.0)

- [ ] US-07 and US-08 pass end to end, including on the demo repository.
- [ ] Strict traceability passes: every M1 and M2 requirement has a task and a test.
- [ ] `v1.0.0` is released.

---

## Phase 7: Proof and polish → M3 (~16 h)

**Goal:** measured performance, a faster cold start, and a portfolio-ready story.
**Read first:** PLAN §17, §21.6, §26

- [ ] **T-7.1** Performance: `perf/seed.sql` (1 million executions) and `perf/dashboard.js`. Measure p95, fix slow queries with `EXPLAIN ANALYZE`, and record the results in `docs/PERFORMANCE.md`. — NFR-PERF-003
- [ ] **T-7.2** Cold start: try the JDK AOT cache (a training run during the Docker build). Measure before and after on Render, and record the numbers. — NFR-PERF-004
- [ ] **T-7.3** `nightly.yml`: all-browser E2E, full PIT, 10,000-case property tests, Trivy rescan. — NFR-QUAL-001, NFR-SEC-005
- [ ] **T-7.4** Optional: `deploy/k8s` manifests and `k8s-smoke.yml` on `kind`. — NFR-OPS-001
- [ ] **T-7.5** Build the first post-v1 feature with the per-feature workflow: [specs/001-ci-feedback](../specs/001-ci-feedback/tasks.md).
- [ ] **T-7.6** Write `docs/RETRO.md`: what went wrong, what you'd change, and numbers (bugs caught by property and mutation tests, CI time, cold start).
- [ ] **T-7.7** Final README pass: architecture diagram, ADR index, "how detection works", a short summary for interviewers. Tag `v1.1.0`.

### Exit criteria (M3: v1.1)

- [ ] Performance results are published, and NFR-PERF-003 is met or the gap is explained.
- [ ] Feature 001 is shipped through the full per-feature workflow.
- [ ] `v1.1.0` is released.
