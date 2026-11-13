# Glossary

Shared words for this project. If a spec, plan, test or UI label uses one of these words, it means exactly what is written here. When a new term comes up, add it here first.

## Tests and results

| Term | Meaning |
|---|---|
| **Test case** | One test, identified across runs by its *test key*. Example: `com.shop.CartTest#addsItem`. |
| **Test key** | The stable name of a test case: `<suite>#<name>` from the report's `classname` and `name` attributes, with whitespace tidied. Parameterised tests keep their parameter suffix, e.g. `CartTest#adds(int)[2]`. |
| **Method key** | The test key without any parameter suffix, e.g. `CartTest#adds`. Quarantine works at this level, so all parameter variations of a method are covered. |
| **Execution** | One attempt at running a test case. A test that failed once and passed on retry has two executions in that upload. |
| **Execution index** | Position of an execution within one upload: `0` is the first try, `1` the first retry, and so on. |
| **Final outcome** | The result of the *last* execution of a test case in one upload. Detection mostly reasons about final outcomes. |
| **Status (of an execution)** | `PASSED`, `FAILED`, `ERRORED` or `SKIPPED`. `ERRORED` (the test crashed instead of failing an assertion) counts as a failure for detection. `SKIPPED` is ignored by detection. |
| **Variant** | An optional label for the environment a test ran in, such as `chrome` or `java-21`. Results from different variants are judged separately, because "fails only on Firefox" is useful information, not noise. |

## CI concepts

| Term | Meaning |
|---|---|
| **Run** | One execution of a CI pipeline, identified by provider + external run ID + attempt. Example: GitHub Actions run `9876543210`, attempt `1`. |
| **Attempt** | The CI system's retry counter for a whole run. Clicking "Re-run failed jobs" in GitHub creates attempt 2 of the same run. |
| **Job key** | Identifies one job or shard inside a run, such as `unit-tests` or `e2e-shard-2`. Defaults to `default`. |
| **Upload** | One request to Flakewatch carrying report files from one job of one run. Uniquely identified by run + job key. |
| **Default branch** | The project's main line of development (usually `main`). Some detection rules only look at default-branch results, because other branches contain unfinished work. |
| **Window** | How far back detection looks. Default: 14 days. |

## Detection

| Term | Meaning |
|---|---|
| **Evidence** | Stored, checkable proof that a test is flaky. There are two kinds: *retry pass* and *same commit, mixed results*. |
| **Retry pass** | Within one upload, a test failed and then passed on a retry. Strongest evidence: same code, same machine, minutes apart. |
| **Same commit, mixed results** | Across uploads for the same commit and variant, the test passed at least once and failed at least once. The code was identical, so the difference came from the test or its environment. |
| **Recovery** | On the default branch, a failed final outcome directly followed by a passed one. Two or more recoveries in the window, without evidence, makes a test *Suspected*. |
| **Stable** | No evidence of flakiness and not currently failing. |
| **Flaky** | Has at least one piece of evidence in the window. |
| **Suspected** | No evidence, but it failed and recovered on its own at least twice in the window. Worth a look, not proven. |
| **Failing** | The latest default-branch result failed, and there is no evidence of flakiness. Possibly a real, new failure. |
| **Broken** | The last 3 default-branch results all failed. Something is really wrong. |
| **Not enough data** | Fewer than 3 results in the window. Too early to say. |
| **Impact** | How much pain a test caused in the window: failed final outcomes plus retries it needed. Used for ranking. |
| **Flip rate** | How often consecutive default-branch results differ, from 0 (never changes) to 1 (changes every time). Shown for information and tie-breaking, never used alone to label a test. |

## Failures

| Term | Meaning |
|---|---|
| **Fingerprint** | A hash of a failure's type, its cleaned message and its top application stack frames. Failures with the same fingerprint are "the same problem". |
| **Normalisation** | Cleaning a failure message so incidental details (numbers, IDs, timestamps, temp paths, line numbers) don't create new fingerprints. |
| **Failure group** | All failures that share a fingerprint. |

## Quarantine

| Term | Meaning |
|---|---|
| **Quarantine** | Marking a test (at method-key level) as known-flaky, so its failures stop breaking builds while it waits to be fixed. |
| **MUTE mode** | The quarantined test still runs. A failure is reported as skipped with a special marker instead of failing the build. Flakewatch reads the marker and still records the real failure. This is the default. |
| **SKIP mode** | The quarantined test does not run at all. No new data is collected for it. |
| **Ready to release** | A quarantined test has passed 20 times in a row across at least 3 different days, so it may be fixed. |

## Engineering terms

| Term | Meaning |
|---|---|
| **Fail-open** | When something goes wrong in our client code, behave as if our tool were not there, rather than failing the user's build. |
| **Idempotent** | Doing it twice has the same effect as doing it once. |
| **Effectively once** | A job may *run* more than once (after a crash, say), but its *effects* are applied exactly once. |
| **Job** | A row in the `jobs` table describing background work, such as "process upload 42". |
| **Lease** | A time-limited claim a worker holds on a job. If the worker dies, the lease expires and another worker can take over. |
| **Fencing token** | A random value issued with each lease. Only the worker holding the current token can mark the job done, so a worker that lost its lease can't overwrite someone else's result. |
| **Ingest token** | A secret that allows uploading results to one project. Starts with `fw_ing_`. |
| **Read token** | A secret that allows reading one project's quarantine list. Starts with `fw_read_`. |
| **Requirement ID** | A stable label for a requirement, such as `FR-ING-004` (functional) or `NFR-SEC-002` (non-functional). Used in tasks, tests, commits and pull requests. |
| **ADR** | Architecture Decision Record: a short document recording one decision, its context and its trade-offs. |
| **Milestone** | A delivery checkpoint. **M1** = live demo, **M2** = v1.0, **M3** = v1.1 polish and proof. |
