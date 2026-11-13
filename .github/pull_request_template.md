## What and why

<!-- One or two sentences. Link the task and requirements. -->

- **Tasks:** T-x.y
- **Requirements:** FR-…, NFR-…

## How it was tested

<!-- Which tests were added? Did you see them fail before the change? -->

## Screenshots (UI changes)

## Definition of Done

- [ ] Each behaviour change has a test that failed before the change, named with its requirement ID
- [ ] CI is green (format, tests, contract, architecture, coverage, mutation if `core` changed, E2E and accessibility, security)
- [ ] `api/openapi.yaml` updated first (if the API changed)
- [ ] Migrations are forward-only and safe during a deploy (if any)
- [ ] Logs and metrics for new background behaviour (if any)
- [ ] Docs updated where needed (spec status, plan, ADR, README, runbook, CHANGELOG)
- [ ] Task ticked in its tasks file

## Constitution exceptions

<!-- "None", or: which rule, why, and the plan to remove the exception. -->
None
