# Feature Spec: CI feedback (pull request comments and alerts)

**Feature:** `001-ci-feedback` · **Status:** Clarified · **Milestone:** M3
**Created:** 2026-11-13 · **Last updated:** 2026-11-13

> This is the first feature built with the per-feature workflow after v1 ([WORKFLOW.md §3](../../docs/WORKFLOW.md#3-adding-a-feature-after-v1)). It is also a worked example of a complete feature spec.

---

## 1. Summary

Bring Flakewatch's answer to where developers already look. A **pull request comment** says which failures in this run are new, broken or known-flaky. **Team alerts** (webhooks, Slack-compatible) announce when a test *becomes* flaky or broken, or recovers.

## 2. Problem

In v1, the answer to "is this failure real?" lives on the Flakewatch dashboard. Developers have to leave GitHub, find the run and read the labels. Many won't bother, and will click "re-run" instead. Also, nobody hears when a test turns flaky until someone happens to open the dashboard.

## 3. Users

| Who | What they want |
|---|---|
| Developer with a red pull request | To see in the PR itself which failures they caused and which are known noise |
| Team (shared chat channel) | To hear once when a test turns flaky or broken, and when it recovers. Never spam. |
| Project owner | To set up alerts in a minute and trust they are secure |

## 4. User stories and acceptance scenarios

### US-F1: Pull request comment (M3)

*As a developer, I see a comment on my pull request that labels each failure, so I know whether to fix my code or ignore known noise.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | A pull request whose run has 1 new failure and 2 known-flaky failures, and the Action has `comment-on-pr: true` | The workflow finishes uploading | One comment appears: new failures first, then broken, then known flaky, each linking to the test's page. |
| 2 | That comment already exists | A new run on the same pull request finishes | The **same** comment is updated. No second comment appears. |
| 3 | All tests passed | The run finishes | The comment is updated to say "No failures in the latest run". |
| 4 | Flakewatch doesn't finish processing within the time budget | The budget runs out | No comment is changed, the step logs a notice, and the job result is unchanged. |
| 5 | The workflow lacks permission to write comments | The step runs | It logs a notice explaining the missing permission, and the job result is unchanged. |

### US-F2: Team alerts (M3)

*As a team, we get one message in our channel when a test becomes flaky or broken, or recovers.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | A webhook subscribed to "became flaky" | A test's status changes to Flaky | One message is delivered, naming the test, the evidence and a link. |
| 2 | 30 tests become flaky from the same upload | The status changes are recorded | Each matching webhook receives **one** message listing up to 10 tests plus "and 20 more". |
| 3 | The same upload is sent again | It is processed | No message is sent (no status changed). |
| 4 | The webhook target returns `500` | Delivery is attempted | It is retried with backoff, up to 6 attempts over about an hour, then marked failed in the delivery history. |

### US-F3: Manage webhooks (M3)

*As a project owner, I add, test and remove webhooks safely.*

| # | Given | When | Then |
|---|---|---|---|
| 1 | My project | I add a webhook with an `https` URL and choose events | It is saved, and its signing secret is shown once. |
| 2 | A URL pointing to a private or local address (e.g. `http://169.254.169.254/`) | I try to save it, or a public name later resolves to such an address | It is refused, and the reason is shown. |
| 3 | A saved webhook | I click "Send test" | A test message is delivered, and the result appears in the delivery history. |

## 5. Requirements

| ID | Requirement | Milestone |
|---|---|---|
| FR-FBK-001 | After uploading, the Action can wait for results (within a time budget) and list each failure of this upload with its label (new failure, broken, known flaky, passed on retry, quarantined) in the job summary. | M3 |
| FR-FBK-002 | Optionally, the Action creates or updates **one** pull request comment with the same information. Each run updates that comment rather than adding a new one. | M3 |
| FR-FBK-003 | The comment orders failures as new, then broken, then known flaky. Each list shows at most 15 items, followed by a link to the full run. | M3 |
| FR-FBK-004 | Feedback steps fail open: timeouts, missing permissions and server errors never change the job result. | M3 |
| FR-FBK-005 | A project owner can register up to 3 webhook URLs per project, each subscribed to any of: became flaky, became broken, recovered. | M3 |
| FR-FBK-006 | Webhook messages are JSON with a `text` field that works directly with Slack incoming webhooks, plus structured fields. Each is signed with HMAC-SHA256 using a per-webhook secret shown once. | M3 |
| FR-FBK-007 | Failed deliveries are retried with backoff (up to 6 attempts over about an hour). The owner can see the last 50 deliveries per webhook. | M3 |
| FR-FBK-008 | Status changes caused by one upload are grouped into one message per webhook. Repeated identical uploads cause no messages. | M3 |
| FR-FBK-009 | A project owner can send a test message to a webhook. | M3 |
| NFR-FBK-001 | Webhook URLs must be `https`. Targets that are, or resolve to, private, loopback, link-local or cloud-metadata addresses are refused, both when saving and when sending. Redirects are not followed. | M3 |
| NFR-FBK-002 | 95% of messages are delivered within 2 minutes of the status change, not counting time the target is down. | M3 |

## 6. Success criteria

- On the demo repository, a pull request with a deliberately flaky test shows a correct comment within 2 minutes of its run finishing.
- During a week of demo activity, alerts match status-change events one to one, with no duplicates.

## 7. Out of scope

- Email, Microsoft Teams- or Discord-specific formats. Generic JSON plus Slack-compatible `text` covers most uses.
- GitHub Checks API annotations.
- Per-user notification preferences.

## 8. Assumptions and dependencies

- Depends on v1: run labels (FR-UI-004), status-change events (FR-DET-009) and the job queue.
- Pull request comments are posted by the Action with the workflow's own `GITHUB_TOKEN`. The Flakewatch server never calls GitHub.

## 9. Clarifications

| Date | Question | Answer |
|---|---|---|
| 2026-11-13 | Should the server post PR comments (it would need a GitHub App), or should the Action? | The Action. The server stays CI-agnostic, and the token never leaves the workflow. |
| 2026-11-13 | Comment per run, or one updated comment? | One updated comment per pull request, found by a hidden marker. Avoids comment spam (Constitution P3). |
| 2026-11-13 | How to avoid 30 alerts when 30 tests turn flaky at once? | Group changes per upload into one message per webhook (FR-FBK-008). |
| 2026-11-13 | Does "recovered" include Suspected → Stable? | No. Only Flaky or Broken → Stable, to keep alerts meaningful. |

## 10. Change log

| Date | Change |
|---|---|
| 2026-11-13 | First version, clarified. |
