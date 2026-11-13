# Product Specification: Flakewatch

**Status:** Approved for v1 · **Spec version:** 1.0 · **Last updated:** 2026-11-13

This document says **what** Flakewatch does and **why**. It deliberately says nothing about *how* it is built; that lives in [PLAN.md](PLAN.md).

Every requirement has an ID. Tasks, tests, commits and pull requests refer to these IDs. Words in this spec are defined in [GLOSSARY.md](GLOSSARY.md).

---

## 1. Summary

Flakewatch collects test results from CI pipelines and tells a team which failing tests are **flaky** (they pass and fail on the same code) and which failures are **real**. For every claim it shows the **evidence**: which runs, which commit, what happened.

It also groups similar failures, ranks flaky tests by how much pain they cause, and lets a team **quarantine** a flaky test so it stops breaking builds while still being tracked.

## 2. The problem

- Flaky tests make CI untrustworthy. When a build goes red, people first ask "is this real?"
- The usual workaround is to click "re-run" until it's green. That wastes CI time, hides real bugs, and teaches people to ignore red builds.
- Most teams *suspect* which tests are flaky but have no proof, no ranking, and no history. So the flaky tests never get fixed.
- Commercial tools exist, but they are paid. Open-source options mostly focus on one framework. Java teams using JUnit or TestNG with Jenkins or GitHub Actions have few lightweight choices.

## 3. Goals and non-goals

**Goals for v1**

1. Answer "is this failure flaky or real?" within seconds of a CI run finishing.
2. Show proof for every flaky label.
3. Work with the report formats teams already produce (JUnit XML), with one CI step to set up.
4. Never make a user's build worse (see [Constitution P2](CONSTITUTION.md#p2-never-break-the-users-build)).
5. Run as a live public demo on free hosting.

**Non-goals for v1**

- Running tests. Flakewatch only reads results.
- Fixing flaky tests automatically, or AI root-cause analysis.
- Teams, organisations, roles or invitations. A project has a single owner in v1.
- Billing, SSO or enterprise features.
- Native report formats other than JUnit XML (for example TestNG's own `testng-results.xml`).

## 4. Who it's for

| Persona | What they want |
|---|---|
| **Developer** (on a team with CI) | To know right away whether a red build is their fault. |
| **Test owner / QA engineer** | A ranked list of flaky tests with proof, to plan fixes and push back on "just re-run it". |
| **Tech lead** | To see whether test reliability is getting better or worse. |
| **Visitor** (recruiter, interviewer, curious engineer) | To explore a live demo without signing up. |

---

## 5. User stories and acceptance scenarios

Priorities use milestones: **M1** = live demo, **M2** = v1.0, **M3** = v1.1 polish and proof.

### US-01: Send results from CI (M1)

*As a developer, I add one step to my CI so test results reach Flakewatch, without slowing down or breaking my build.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | A project with an ingest token, and a CI job that wrote JUnit XML files | The upload step runs after the tests | The files are accepted within 1 s, and a link to the run is shown in the job summary. |
| 2 | Flakewatch is down | The upload step runs | The step prints a warning and the job's result is unchanged. |
| 3 | A fork pull request, where secrets aren't available | The upload step runs | It skips with a notice explaining why. |
| 4 | The same upload is sent twice (CI retried the step) | The second request arrives | Nothing is duplicated, and the original upload is returned. |

### US-02: "Is this failure real?" (M1)

*As a developer looking at a red build, I can tell within seconds whether each failed test is known to be flaky or is a new failure.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | A run with 3 failed tests: one Flaky, one Stable until now, one Broken | I open the run page | Each failure is labelled: "known flaky", "new failure" and "broken", with a link to the test's history. |
| 2 | A test failed, then passed on retry in this run | I open the run page | It is shown as "passed on retry", and the test's status becomes Flaky. |

### US-03: Ranked list of flaky tests (M1)

*As a test owner, I see flaky tests ranked by impact, with proof, so I know what to fix first.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | A project with Flaky and Suspected tests | I open the tests list filtered to Flaky | Tests are sorted by impact, highest first, each showing impact, flip rate and last failure time. |
| 2 | A test is Suspected but not Flaky | I look at its badge | The badge says "Suspected", and its tooltip explains that it isn't proven yet. |
| 3 | More than 50 matching tests | I scroll to the end | The next page loads, and the URL lets me share the exact filter. |

### US-04: One test's history and proof (M1)

*As a test owner, I see one test's history and the evidence behind its status.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | A Flaky test | I open its page | I see its status per variant, the last 50 results as a timeline, and an evidence list. Each evidence item links to the run(s) and commit that prove it. |
| 2 | A test that is flaky only on the `firefox` variant | I open its page | The overall status is Flaky; the per-variant view shows `firefox: Flaky`, `chrome: Stable`. |

### US-05: Grouped failures (M1)

*As a developer, I see similar failures grouped, so 40 timeouts read as one problem.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | Failures "Timeout after 3012 ms" and "Timeout after 2987 ms" with the same top app stack frames | They are processed | They share one failure group. |
| 2 | Same message but different exception types | They are processed | They are in different groups. |

### US-06: Sign in, create project, manage tokens (M2)

*As a project owner, I sign in, create a project and manage its tokens.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | I'm not signed in | I click "Sign in with GitHub" and approve | I'm signed in and see my projects. |
| 2 | I'm signed in | I create a project with a valid name and slug | It appears in my list, private by default. |
| 3 | A project of mine | I create an ingest token | The full token is shown once. Afterwards only its prefix is visible. |
| 4 | A revoked token | It is used for an upload | The upload is rejected with `401`. |

### US-07: Quarantine a flaky test (M2)

*As a project owner, I quarantine a flaky test so it stops failing builds but keeps reporting.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | A Flaky test | I quarantine it with a reason | It shows a "Quarantined" badge, and the read API includes its method key. |
| 2 | A project using the JUnit extension in MUTE mode | The quarantined test fails | The build passes; the test shows as skipped with the quarantine marker; Flakewatch records the real failure. |
| 3 | Flakewatch is unreachable | The test suite starts | Nothing is quarantined, a warning is logged, and tests behave normally. |

### US-08: Know when a fix worked (M2)

*As a project owner, I'm told when a quarantined test looks fixed.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | A quarantined test that has passed 20 times in a row across 3+ days | I open the quarantine list | It shows "Ready to release". |

### US-09: Explore a live demo (M1)

*As a visitor, I can explore a live public demo without signing up.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | The public demo project | I open the live link | I can browse every read-only page with real, recent data. |
| 2 | A private project | I open its URL without signing in | I get "not found" (we don't reveal that it exists). |

### US-10: PR feedback and alerts (M3, separate feature)

*As a developer, I get a pull request comment listing new failures versus known-flaky ones, and my team gets an alert when a test becomes flaky or broken.*

Specified separately in [specs/001-ci-feedback/spec.md](../specs/001-ci-feedback/spec.md). It is the first feature to go through the per-feature workflow after v1.

---

## 6. Functional requirements

### Ingestion (FR-ING)

| ID | Requirement | Milestone |
|---|---|---|
| FR-ING-001 | Accept an upload of 1 to 20 JUnit XML report files (plain or gzip-compressed) in one request, authenticated with a project ingest token. | M1 |
| FR-ING-002 | Each upload carries metadata: commit SHA, branch and CI run ID (required); CI provider, run attempt (default 1), job key (default `default`), variant, pull request number, run start time and repository URL (optional). | M1 |
| FR-ING-003 | Reply `202 Accepted` with an upload ID and a status URL. Parsing happens in the background. | M1 |
| FR-ING-004 | Repeat uploads are safe. An upload with the same project + provider + run ID + attempt + job key returns the original (`200`) if the content is identical, and `409 Conflict` if it differs. | M1 |
| FR-ING-005 | An upload status endpoint shows received, processing, processed or failed, plus counts (tests, passed, failed, errored, skipped, retried) and any parse warnings. | M1 |
| FR-ING-006 | Parse reports produced by Maven Surefire/Failsafe, Gradle, TestNG (JUnit-style reports), Playwright, WebdriverIO and jest-junit. Each producer is covered by real sample files. | M1 |
| FR-ING-007 | Recognise retries inside one upload: Surefire `flakyFailure`/`flakyError` and `rerunFailure`/`rerunError` elements, and repeated entries for the same test. | M1 |
| FR-ING-008 | Reject invalid requests immediately with a clear problem response: missing or invalid metadata, too many files, files too large, or a file that isn't a JUnit XML report (wrong root element). Problems found later, during parsing, are reported through the upload status with a reason. | M1 |
| FR-ING-009 | Tolerate small problems: skip a malformed test entry with a warning instead of failing the whole upload. | M1 |
| FR-ING-010 | For every execution, store test identity, status, duration and execution index. For failures, also store type, message and stack trace, trimmed to set limits. | M1 |
| FR-ING-011 | Group uploads from parallel jobs or shards of one CI run under one run. Reject an upload whose run metadata (commit, branch) contradicts an earlier upload for the same run. | M1 |
| FR-ING-012 | Keep raw uploaded files for 7 days, then delete them automatically. | M1 |
| FR-ING-013 | Recognise failures muted by quarantine (the marker in a skipped entry) and record them as failures flagged as quarantined. | M2 |

### Detection (FR-DET)

Exact rules, examples and properties: [design/detection-rules.md](design/detection-rules.md).

| ID | Requirement | Milestone |
|---|---|---|
| FR-DET-001 | Give every test case exactly one status per variant, and one overall status: Stable, Flaky, Suspected, Failing, Broken or Not enough data. | M1 |
| FR-DET-002 | Mark a test Flaky when, within one upload, it failed and then passed on a retry ("retry pass" evidence). | M1 |
| FR-DET-003 | Mark a test Flaky when it both passed and failed on the same commit and variant, on any branch ("same commit, mixed results" evidence). | M1 |
| FR-DET-004 | Mark a test Suspected when, on the default branch within the window, it failed and then recovered on its own at least twice, and there is no evidence. | M1 |
| FR-DET-005 | Mark a test Broken when its last 3 default-branch results failed, and Failing when only its latest default-branch result failed. | M1 |
| FR-DET-006 | The same set of results always gives the same statuses, whatever order they were uploaded in and however many duplicates arrived. | M1 |
| FR-DET-007 | Show the evidence behind every Flaky status: kind, run(s), commit, variant, time and a one-line explanation. | M1 |
| FR-DET-008 | Update statuses within 60 seconds of an upload being processed. | M1 |
| FR-DET-009 | Record each status change (from, to, when, why) as an event. | M1 |
| FR-DET-010 | Re-check statuses daily, so evidence older than the window stops counting. | M1 |
| FR-DET-011 | Let a project owner change detection settings (window days, recoveries needed for Suspected, failures in a row for Broken) within safe limits. | M2 |
| FR-DET-012 | Compute impact and flip rate for every test, for ranking and display. | M1 |

### Failure grouping (FR-GRP)

| ID | Requirement | Milestone |
|---|---|---|
| FR-GRP-001 | Group failures by fingerprint: failure type + normalised message + top application stack frames. | M1 |
| FR-GRP-002 | Normalisation ignores numbers, durations, UUIDs, long hex strings, timestamps, temporary paths and stack-trace line numbers. | M1 |
| FR-GRP-003 | For each group, show the occurrence count, affected tests, first and last seen times, and a sample of the original message. | M1 |

### Projects and access (FR-PRJ)

| ID | Requirement | Milestone |
|---|---|---|
| FR-PRJ-001 | Users sign in with GitHub. | M2 |
| FR-PRJ-002 | A signed-in user can create a project with a name, a slug, a visibility (public or private, default private) and a default branch. | M2 |
| FR-PRJ-003 | A project owner can create, list and revoke ingest and read tokens. The full token is shown only once; afterwards only a prefix is visible. | M2 |
| FR-PRJ-004 | Anyone can read public projects. Only the owner can read private projects. To everyone else a private project looks like it doesn't exist. | M1 |
| FR-PRJ-005 | A token works for exactly one project and one scope (ingest or read). | M1 |
| FR-PRJ-006 | A project owner can edit project settings, and delete a project with all its data. | M2 |
| FR-PRJ-007 | An operator can create a project and get an ingest token from the command line, before sign-in exists. | M1 |

### Dashboard (FR-UI)

| ID | Requirement | Milestone |
|---|---|---|
| FR-UI-001 | Project overview: number of tests per status, flaky-count trend for the last 30 days, and recent runs. | M1 |
| FR-UI-002 | Tests list: filter by status, search by name, sort by impact (default) or recency, paginated. | M1 |
| FR-UI-003 | Test detail: status per variant, timeline of the last 50 results, evidence list linking to runs, failure groups, and duration trend. | M1 |
| FR-UI-004 | Runs list and run detail. In a run, each failed test is labelled "known flaky", "new failure", "broken" or "passed on retry". | M1 |
| FR-UI-005 | Failure groups list and group detail. | M1 |
| FR-UI-006 | Settings pages: tokens, detection settings, visibility, and project deletion. | M2 |
| FR-UI-007 | Every page has loading, empty and error states. Every view has a shareable URL that restores its filters. | M1 |
| FR-UI-008 | Quarantine controls (quarantine with reason, release), a quarantined-tests list and "ready to release" hints. | M2 |

### CI integration (FR-CI)

| ID | Requirement | Milestone |
|---|---|---|
| FR-CI-001 | A GitHub Action uploads report files matched by a glob pattern, filling in metadata from the GitHub Actions environment. | M1 |
| FR-CI-002 | Fail-open by default: upload problems never fail the build unless `fail-on-error: true` is set. | M1 |
| FR-CI-003 | Retry with backoff on network errors, `429` and `5xx` responses, and tolerate a slow-waking server (default timeout 120 s). | M1 |
| FR-CI-004 | Skip with a clear notice when the token is missing (for example on fork pull requests). | M1 |
| FR-CI-005 | Write a job summary with counts and a link to the run in Flakewatch. | M1 |
| FR-CI-006 | Document how to upload from Jenkins (a `Jenkinsfile` example) and from any CI using `curl`. | M1 |

### Quarantine (FR-QUA)

| ID | Requirement | Milestone |
|---|---|---|
| FR-QUA-001 | A project owner can quarantine a test (at method-key level) with a reason, and release it. History is kept. | M2 |
| FR-QUA-002 | A read API returns the project's active quarantine list, supporting ETag caching. | M2 |
| FR-QUA-003 | A JUnit Jupiter extension supports MUTE mode (quarantined tests run, but their failures don't fail the build) and SKIP mode (they don't run). | M2 |
| FR-QUA-004 | A TestNG listener supports the same two modes. | M2 |
| FR-QUA-005 | The libraries fail open: if the server can't be reached within 3 s, nothing is quarantined and a warning is logged. | M2 |
| FR-QUA-006 | Mark a quarantined test "ready to release" after 20 passes in a row across at least 3 different days. | M2 |

---

## 7. Non-functional requirements

### Performance (NFR-PERF)

| ID | Requirement | Milestone |
|---|---|---|
| NFR-PERF-001 | Accept (`202`) an upload of up to 5 MB in under 1 s at the 95th percentile, not counting host cold start. | M1 |
| NFR-PERF-002 | Fully process an upload of 10,000 test cases in under 15 s on the reference machine (a standard GitHub-hosted Linux runner). | M1 |
| NFR-PERF-003 | Dashboard API calls respond in under 300 ms at the 95th percentile with 1 million stored executions. | M3 |
| NFR-PERF-004 | The service runs inside a 512 MB container. | M1 |

### Reliability (NFR-REL)

| ID | Requirement | Milestone |
|---|---|---|
| NFR-REL-001 | An accepted upload is never lost. It is processed, or marked failed with a reason, even across restarts and host sleep. | M1 |
| NFR-REL-002 | Processing is effectively once: re-processing never double-counts results, statistics or failure groups. | M1 |
| NFR-REL-003 | On shutdown, in-flight work finishes or is released within 30 s and is picked up again after restart. | M1 |

### Security (NFR-SEC)

| ID | Requirement | Milestone |
|---|---|---|
| NFR-SEC-001 | Tokens have at least 256 bits of randomness, are stored only as hashes, are shown once, and are redacted from logs. | M1 |
| NFR-SEC-002 | XML parsing rejects DTDs and external entities. Limits on request size, file count, decompressed size, nesting depth and field length are enforced. | M1 |
| NFR-SEC-003 | Every API operation has automated authorization tests for every type of caller. | M1 |
| NFR-SEC-004 | Rate limits apply per token for uploads and per IP address for public reads. | M1 |
| NFR-SEC-005 | CI blocks merging on critical vulnerabilities in dependencies or the container image, and on code-scanning alerts. | M1 |
| NFR-SEC-006 | Browser sessions use Secure, HttpOnly, SameSite cookies, with CSRF protection on state-changing requests. Responses send security headers (CSP, HSTS, and others). | M2 |

### Observability (NFR-OBS)

| ID | Requirement | Milestone |
|---|---|---|
| NFR-OBS-001 | Logs are structured JSON and include request, project, upload and job IDs where relevant. | M1 |
| NFR-OBS-002 | Metrics cover uploads (accepted and rejected), job durations and outcomes, queue depth, and the age of the oldest waiting job. | M1 |
| NFR-OBS-003 | The liveness check never touches the database; the readiness check does. | M1 |

### Cost (NFR-COST)

| ID | Requirement | Milestone |
|---|---|---|
| NFR-COST-001 | Runs on free tiers. Database storage stays under 1 GB through retention, and the database can sleep when the app is idle (no constant polling). | M1 |

### Data (NFR-DATA)

| ID | Requirement | Milestone |
|---|---|---|
| NFR-DATA-001 | Retention defaults: executions 90 days, raw files 7 days, daily statistics 400 days. All are configurable. | M1 |

### Quality (NFR-QUAL)

| ID | Requirement | Milestone |
|---|---|---|
| NFR-QUAL-001 | Server line coverage ≥ 80%. Mutation score of the `core` module ≥ 75%. | M1 |
| NFR-QUAL-002 | Architecture rules (module boundaries, no direct clock or randomness) are enforced by tests. | M1 |
| NFR-QUAL-003 | The public API matches `api/openapi.yaml`, checked by tests on every build. | M1 |
| NFR-QUAL-004 | Flakewatch's own test suite has no known flaky tests on `main`, verified by uploading its own results. | M1 |

### Accessibility (NFR-A11Y)

| ID | Requirement | Milestone |
|---|---|---|
| NFR-A11Y-001 | Core dashboard pages meet WCAG 2.2 AA. Automated axe checks report no serious or critical issues, and status is never shown by colour alone. | M1 |

### Operations (NFR-OPS)

| ID | Requirement | Milestone |
|---|---|---|
| NFR-OPS-001 | One Docker image, configured only through environment variables. It runs locally with one command. | M1 |
| NFR-OPS-002 | Every merge to `main` deploys automatically after all checks pass. Rolling back to the previous version takes under 5 minutes. | M1 |

---

## 8. Supported inputs

| Producer | How it's produced | Retry information |
|---|---|---|
| Maven Surefire / Failsafe | `TEST-*.xml` | `flakyFailure`, `flakyError`, `rerunFailure`, `rerunError` child elements |
| Gradle | `build/test-results/**/TEST-*.xml` | Repeated `testcase` entries when the test-retry plugin is used |
| TestNG | JUnit-style reports (`junitreports/*.xml`) | Retried attempts appear as extra entries |
| Playwright | `junit` reporter | Depends on reporter settings; captured in sample files |
| WebdriverIO | `@wdio/junit-reporter` | Captured in sample files |
| Jest | `jest-junit` | Usually none |

The exact behaviour of each producer is fixed by **real sample files** captured from each tool, kept in the repository as test fixtures. Hand-written samples are allowed only for malformed or malicious cases, and must be labelled as such.

## 9. Statuses as users see them

| Status | Shown as | One-line explanation in the UI |
|---|---|---|
| Stable | "Stable" | "No sign of flakiness and passing." |
| Flaky | "Flaky" | "Proven: it passed and failed on the same code." |
| Suspected | "Suspected" | "Failed and recovered on its own at least twice. Not proven yet." |
| Failing | "Failing" | "Failed on the latest default-branch run. May be a real failure." |
| Broken | "Broken" | "Failed on the last 3 default-branch runs." |
| Not enough data | "Not enough data" | "Fewer than 3 results so far." |

Status is always shown as text plus an icon, never by colour alone ([NFR-A11Y-001](#accessibility-nfr-a11y)).

## 10. Success criteria

| ID | Criterion |
|---|---|
| SC-01 | The live link shows real data from the demo repository, updated at least daily. |
| SC-02 | Someone new to the project can run it locally with `docker compose up` in under 10 minutes by following the README. |
| SC-03 | CI on `main` finishes in under 12 minutes. |
| SC-04 | Every M1 and M2 requirement links to at least one task and one automated test (`scripts/check-docs.mjs --strict` passes). |
| SC-05 | At least 8 ADRs are recorded, and every plan passes the constitution check. |
| SC-06 | On the demo repository, each deliberately flaky test is Flaky or Suspected within 2 days, stable tests stay Stable, and the deliberately broken test is Broken. |

## 11. Out of scope for v1

- Multiple owners or members per project, roles and invitations.
- Other CI-specific integrations, beyond the GitHub Action and the documented `curl`/Jenkins recipe.
- Report formats other than JUnit XML.
- Machine-learning detection, automatic fixes, root-cause analysis.
- Emails. Alerts are webhooks only, planned in [specs/001-ci-feedback](../specs/001-ci-feedback/spec.md).

## 12. Assumptions and constraints

- Free hosting: a small container (512 MB RAM) that sleeps when idle, plus a managed PostgreSQL that also sleeps when idle and has 1 GB of storage. See [ADR-0006](adr/0006-hosting-render-and-neon.md).
- Single instance. The design must still be safe if two instances ever run at once (for example during a deploy).
- One part-time developer, so simplicity matters ([Constitution Article 4](CONSTITUTION.md#article-4-simple-by-default)).

## 13. Open questions

None block M1.

| ID | Question | Decide by |
|---|---|---|
| Q-01 | Final product name ("Flakewatch" is a working name). | Before the M1 launch |
| Q-02 | Publish the quarantine libraries to Maven Central (needs namespace verification and signing) or GitHub Packages (needs a token even to download)? | Start of Phase 6 |

## 14. Change log

| Version | Date | Change |
|---|---|---|
| 1.0 | 2026-11-13 | First approved version. |
