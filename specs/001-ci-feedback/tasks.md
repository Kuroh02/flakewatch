# Feature Tasks: CI feedback

**Feature:** `001-ci-feedback` · **Spec:** [spec.md](spec.md) · **Plan:** [plan.md](plan.md)

---

## Setup

- [ ] **F001-T01** Add the new operations to `api/openapi.yaml`; lint clean; regenerate frontend types. — FR-FBK-001, FR-FBK-005, FR-FBK-007, FR-FBK-009
- [ ] **F001-T02** Flyway `V6` (`uploads.classified_at`, `webhooks`, `webhook_deliveries`); set `classified_at` in the reclassification transaction. — FR-FBK-001

## US-F1: Pull request comment

- [ ] **F001-T03** Tests first: `UploadFeedbackIT` (`202` before classification, `200` with labels after, ingest token of another project → `404`). Then implement the endpoint. — FR-FBK-001
- [ ] **F001-T04** Tests first: Action polling with a time budget, and the job-summary renderer (snapshot tests for ordering and caps). Then implement. — FR-FBK-001, FR-FBK-003
- [ ] **F001-T05** Tests first, with a mocked GitHub API: create the comment, find it by marker and update it; "no failures" text; `403` → notice. Then implement. — FR-FBK-002, FR-FBK-004
- [ ] **F001-T06** Fail-open tests for every feedback path: timeout, `500`, network error. — FR-FBK-004

## US-F2 and US-F3: Webhooks

- [ ] **F001-T07** Tests first: `SafeUrlValidator` address table (IPv4 and IPv6) and DNS rebinding with a fake resolver. Do the Spring Boot SSRF spike, then implement. — NFR-FBK-001
- [ ] **F001-T08** Tests first: HMAC known-vector test; AES-GCM encrypt and decrypt round trip; the secret is never logged. Then implement the signer and secret storage. — FR-FBK-006
- [ ] **F001-T09** Webhooks API: create (secret shown once, limit of 3), list, delete; authorization-matrix entries. — FR-FBK-005
- [ ] **F001-T10** Tests first: grouping property (N changes in one upload → 1 delivery per matching webhook) and `ReuploadSendsNothingIT`. Then implement the `SEND_NOTIFICATIONS` job, queued in the reclassification transaction. — FR-FBK-008
- [ ] **F001-T11** Tests first: `WebhookRetryIT` against a stub server (`500`, then `200`; always `500` → `FAILED` after 6 attempts). Then implement the `DELIVER_WEBHOOK` job and delivery history. — FR-FBK-007, NFR-FBK-002
- [ ] **F001-T12** Test-message endpoint. — FR-FBK-009
- [ ] **F001-T13** UI: webhooks section in settings (add with event choices, copy-once secret, send test, delivery history). Component and E2E tests. — FR-FBK-005, FR-FBK-007, FR-FBK-009
- [ ] **F001-T14** Metrics and logs for deliveries; runbook entries (delivery history, key rotation). — NFR-FBK-002

## Finish

- [ ] **F001-T15** The demo repository opens a pull request with a flaky test and `comment-on-pr: true`. Verify every US-F1 scenario. Connect a real Slack channel and verify US-F2. — FR-FBK-002, FR-FBK-006
- [ ] **F001-T16** Docs: Action README (new inputs, permissions), webhook signature verification guide, CHANGELOG. Set the spec status to Shipped. — FR-FBK-006

## Done when

- [ ] Every acceptance scenario in `spec.md` passes as an automated test (or, for real Slack and GitHub, is verified on the demo repository and recorded).
- [ ] `node scripts/check-docs.mjs` passes.
- [ ] Deployed, and the smoke test passed.
