# Feature Tasks: <Feature name>

**Feature:** `NNN-short-name` · **Spec:** [spec.md](spec.md) · **Plan:** [plan.md](plan.md)

> Each task takes half a day or less, lists its requirement IDs, and puts tests before the code they test.
> Tick the box in the pull request that finishes the task.

---

## Setup

- [ ] **FNNN-T01** <setup or migration task> — FR-<AREA>-001

## Story US-<n>: <title>

- [ ] **FNNN-T02** Tests first: <the failing tests to write> — FR-<AREA>-001
- [ ] **FNNN-T03** Implement <the smallest thing that makes them pass> — FR-<AREA>-001
- [ ] **FNNN-T04** <UI / docs / metrics task> — FR-<AREA>-002

## Finish

- [ ] **FNNN-T0x** End-to-end test for every acceptance scenario in the spec.
- [ ] **FNNN-T0x** Docs: CHANGELOG, README or runbook if affected; spec status → Shipped.

## Done when

- [ ] Every acceptance scenario in `spec.md` passes as an automated test.
- [ ] `node scripts/check-docs.mjs` passes.
- [ ] Deployed, and the smoke test passed.
