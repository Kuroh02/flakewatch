# Detection Rules

**Implements:** FR-DET-001 to FR-DET-012 · **Decision record:** [ADR-0004](../adr/0004-evidence-based-detection-rules.md)

This is the exact definition of how Flakewatch decides a test's status. It is written so that each rule, example and property can be turned directly into a test. If the code and this document disagree, the code is wrong (or this document needs a reviewed change first).

---

## 1. The idea in one paragraph

A test is **Flaky** only when we have **proof**: it passed and failed on the same code. Everything else is a softer label. **Suspected** means it keeps failing and recovering on its own. **Failing** means its latest result was red. **Broken** means it has been red for a while. We judge each environment (variant) separately, and we only look at recent results (the window).

## 2. Inputs

The detector is a **pure function**: no database, no clock, no randomness inside it.

```
classify(executions, settings, now, defaultBranch) -> Classification
```

| Input | Contents |
|---|---|
| `executions` | Every execution of **one test case** with `executedAt` inside the window. Each one carries: provider, external run ID, attempt, job key, variant, branch, commit SHA, execution index, status, executed-at time. |
| `settings` | `windowDays`, `suspectedRecoveries`, `brokenStreak` (see [section 10](#10-settings-and-defaults)). |
| `now` | The current time, passed in from the injected `Clock`. |
| `defaultBranch` | The project's default branch name. |

The output holds: the status per variant, the overall status, up to 5 evidence items, impact, and flip rate.

## 3. Preparing the data

Do these steps in order:

1. **Drop old data.** Ignore executions with `executedAt < now - windowDays`.
2. **Drop duplicates.** Two executions with the same *identity* (provider, external run ID, attempt, job key, test key, execution index) count once.
3. **Sort.** Order executions by (executed-at, provider, external run ID, attempt, job key, execution index). Every part of this key comes from the data itself, never from database IDs or arrival order. This is what makes the result independent of upload order.
4. **Split by variant.** Each variant (including the empty default variant `""`) is judged on its own.
5. **Find final outcomes.** For each upload (run + job key), the final outcome of the test is its last execution. `ERRORED` counts as `FAILED`. `SKIPPED` executions are dropped.

From this, for each variant we have:

- **A**: all final outcomes in the window, on any branch.
- **D**: the final outcomes on the **default branch** only, oldest to newest.

## 4. Evidence

Evidence is proof that a test is flaky. There are exactly two kinds.

| Kind | Found when | Why it's proof |
|---|---|---|
| `RETRY_PASS` | Within one upload, the test has a failed execution followed by a passed one. | Same code, same machine, minutes apart. |
| `SAME_COMMIT_MIXED` | For one commit and one variant, the test has at least one passed and at least one failed final outcome, across different uploads, on any branch. This also catches a CI "re-run" of the same commit. | The code was identical, so the difference came from the test or its environment. |

Each evidence item stores: kind, run ID(s), commit SHA, variant, time, and a one-line explanation, for example *"Failed, then passed on retry 1 in run 9876543210 (commit 3f2a9c1)"*. The detector returns the **5 most recent** items.

A retry that never passes (all attempts failed, like Surefire's `rerunFailure`) is **not** evidence. It is just a failure.

## 5. The rules

For each variant, apply the rules **in this order**. The first rule that matches decides the status.

| Order | Status | Rule |
|---|---|---|
| 1 | **Broken** | D has at least `brokenStreak` (default 3) results, and the last `brokenStreak` are all failures. |
| 2 | **Flaky** | There is at least one evidence item in the window. |
| 3 | **Failing** | The last result in D is a failure. |
| 4 | **Suspected** | D contains at least `suspectedRecoveries` (default 2) *recoveries*. A recovery is a failure immediately followed by a pass. |
| 5 | **Not enough data** | A has fewer than 3 results. |
| 6 | **Stable** | None of the above. |

**Why this order?**

- *Broken before Flaky:* a test that has failed 3 times in a row needs attention now, even if it was flaky last week.
- *Flaky before Failing:* a proven-flaky test that just failed once is most likely flaky again, so we label it that way.
- *Failing before Suspected:* "red right now" is more actionable than "has a wobbly history".

## 6. Combining variants

The overall status is the variant status that comes first in this **attention order**:

> Broken → Flaky → Failing → Suspected → Stable → Not enough data

This is the same order as the rules. Example: `chrome: Stable` and `firefox: Flaky` gives overall **Flaky**. The test page always shows the per-variant breakdown, so the user can see it is a Firefox-only problem.

## 7. Properties that must always hold

These are tested with property-based tests (hundreds of generated histories per run, seed printed on failure). IDs are used in test names.

| ID | Property |
|---|---|
| PROP-DET-01 | **Total.** Every input, including an empty one, gives exactly one status per variant and one overall status. An empty input gives Not enough data. |
| PROP-DET-02 | **Order doesn't matter.** Shuffling the input list never changes the result. |
| PROP-DET-03 | **Duplicates don't matter.** Adding copies of existing executions (same identity) never changes the result. |
| PROP-DET-04 | **No proof, no Flaky.** If no evidence exists, the status is never Flaky. |
| PROP-DET-05 | **All passes are clean.** If every result passed, the status is Stable or Not enough data. |
| PROP-DET-06 | **Proof shows.** If evidence exists in the window, the status is Flaky or Broken. |
| PROP-DET-07 | **Old data is ignored.** Adding executions older than the window never changes the result. |
| PROP-DET-08 | **Passes can't hurt a clean test.** If a variant has no failed results in the window, adding passed results can only change its status from Not enough data to Stable. |
| PROP-DET-09 | **Variant combination.** The overall status always equals the first per-variant status in attention order. |
| PROP-DET-10 | **Flip rate is sane.** Always between 0 and 1. A constant history gives 0; a strictly alternating history gives 1. |
| PROP-DET-11 | **Impact grows with failures.** Impact is never negative, and adding a failed result in the window never lowers it. |

PROP-DET-08 matters for performance. It proves that after an upload we only need to recheck some tests (see [section 11](#11-when-statuses-are-recalculated)).

## 8. Worked examples

Each row becomes a parameterised test named after its ID.

**How to read the history:** final outcomes on the default branch, oldest → newest. `P` = passed, `F` = failed, `S` = skipped. `[F→P]` means "failed, then passed on retry within one upload". Each result is a different commit unless stated. Everything is inside the window unless stated. Default settings.

| ID | History | Evidence | Status | Reason |
|---|---|---|---|---|
| E01 | `P P P P P` | none | **Stable** | No rule 1–5 matches. |
| E02 | `P P` | none | **Not enough data** | Only 2 results (rule 5). |
| E03 | `P P [F→P] P P` | retry pass | **Flaky** | Rule 2. The retried upload's final outcome is P. |
| E04 | `P P F P`, where the last `F` and `P` are the same commit | same commit, mixed | **Flaky** | Rule 2. A re-run of one commit gave different results. |
| E05 | `P P P F F F` | none | **Broken** | Last 3 failed (rule 1). |
| E06 | `P P P P F` | none | **Failing** | Latest failed, no evidence (rule 3). |
| E07 | `P F P P F P P` | none | **Suspected** | 2 recoveries (rule 4). |
| E08 | `P P F P P P` | none | **Stable** | Only 1 recovery. |
| E09 | `P [F→P] P F F F` | retry pass | **Broken** | Rule 1 comes before rule 2. |
| E10 | `P [F→P] P P F` | retry pass | **Flaky** | Rule 2 comes before rule 3. |
| E11 | A retry pass 20 days ago; since then `P P P P` | outside window | **Stable** | Old evidence no longer counts. |
| E12 | No default-branch results. On branch `feature/x`, commit `c9` ran twice: `P`, then `F`. | same commit, mixed | **Flaky** | Evidence counts from any branch. |
| E13 | `P S S P` | none | **Not enough data** | Skips are ignored, leaving 2 results. |
| E14 | `P P [F→F→F]` | none | **Failing** | Retries that never pass are not evidence. |
| E15 | `chrome`: `P P P P`; `firefox`: `P [F→P] P` | firefox: retry pass | **Flaky** overall (chrome Stable, firefox Flaky) | Combine by attention order. |
| E16 | `chrome`: `P P P`; `firefox`: `P P F`. The last chrome `P` and the last firefox `F` are the same commit. | none | **Failing** overall (chrome Stable, firefox Failing) | Different variants never form evidence together. |
| E17 | *(no executions)* | none | **Not enough data** | The function is total. |

## 9. Impact and flip rate

Both are computed per variant, then summed (impact) or taken as the maximum (flip rate) for the overall test.

**Impact** answers "how much pain did this test cause in the window?"

```
impact = (failed final outcomes in A) + (uploads in which the test needed a retry)
```

The tests list sorts by impact by default.

**Flip rate** answers "how often does the result change between runs?"

```
flips     = number of positions i in D where D[i] != D[i-1]
flip_rate = flips / (len(D) - 1)     when len(D) >= 2, otherwise 0
```

Flip rate is **shown, never used alone to label a test**. It depends heavily on the number of runs, so one failure among 10 runs looks very different from one failure among 50.

## 10. Settings and defaults

| Setting | Default | Allowed range | Who can change it |
|---|---|---|---|
| `windowDays` | 14 | 7–60 | Project owner (FR-DET-011) |
| `suspectedRecoveries` | 2 | 2–5 | Project owner |
| `brokenStreak` | 3 | 2–10 | Project owner |
| Minimum results for a verdict | 3 | fixed | Nobody |
| Maximum executions read per variant | 200 most recent | fixed | Nobody (protects performance) |
| Ready-to-release passes | 20 in a row, across ≥ 3 days | fixed in v1 | Nobody |

Changing settings recalculates every test in the project, using a background job.

## 11. When statuses are recalculated

1. **After an upload is processed.** A `RECLASSIFY_TESTS` job is queued for the tests in the upload that:
   - had a failed execution in this upload, or
   - had any failure in the window (from `last_failure_at`), or
   - are not currently Stable (this includes Not enough data), or
   - are new.

   Every other test in the upload is Stable with no failures in the window. By PROP-DET-08, extra passes can't change its status, so it is safely skipped. For a typical upload this skips more than 95% of the work.
2. **Daily.** A `RECLASSIFY_AGING` job rechecks every test in each project, in batches of 500, so expired evidence and stale results stop counting (FR-DET-010). At v1 scale this is cheap. Revisit if a project passes 50,000 tests.
3. **After settings change.** The whole project is rechecked.

Whenever a test's overall status changes, a row is added to `test_status_events` with from, to, time and reason (FR-DET-009).

## 12. Why these rules? (FAQ)

**Why not just use the failure rate?**
A test that was genuinely broken for 3 days and then fixed has a high failure rate but is not flaky. Failure rate mixes up "broken for a while" and "randomly unreliable". Evidence and recoveries tell them apart.

**Why not use flip rate with a threshold?**
Flip rate depends on how many runs there were. One failure among 10 runs gives 0.22, but among 12 runs it gives 0.18. A fixed threshold would label the same behaviour differently depending on sample size. Counting *recoveries* doesn't have this problem.

**Why can a PR branch produce Flaky evidence, but not Suspected or Failing?**
Same-commit evidence is about identical code, and that's true on any branch. A pattern of failures and recoveries on a feature branch is often just work in progress (break, fix, break, fix), so only the default branch counts for those rules.

**Why judge variants separately?**
"Fails only on Firefox" or "fails only on Java 21" is a real, useful finding. Mixing variants would create false recoveries (`chrome P`, `firefox F`, `chrome P`…) and hide the actual pattern.
