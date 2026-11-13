# Feature Plan: <Feature name>

**Feature:** `NNN-short-name` · **Spec:** [spec.md](spec.md) · **Status:** Draft | Approved
**Constitution version:** <x.y.z> · **Last updated:** YYYY-MM-DD

> Say **how**. Every requirement in the spec must map to a design element and a test approach.

---

## 1. Summary

<The approach in a few sentences.>

## 2. Constitution check

Fill this in **first**. Any ❌ needs an entry in section 10.

| Rule | How this plan complies | ✅ / ❌ |
|---|---|---|
| P1 Evidence over verdicts | | |
| P2 Never break the user's build | | |
| P3 Signal over noise | | |
| 1 Spec first, traceable | | |
| 2 Tests first | | |
| 3 Data correctness | | |
| 4 Simple by default | | |
| 5 Contract first | | |
| 6 Secure by default | | |
| 7 Deterministic | | |
| 8 Observable and operable | | |
| 9 Small steps, decisions written down | | |

## 3. Design

<Components touched, new modules or classes, sequence diagram if helpful (Mermaid).>

## 4. Data changes

<New or changed tables and columns, written as migrations. Explain why each is safe while the old and new versions run side by side during a deploy.>

## 5. API changes

<Operations added or changed. **Update `api/openapi.yaml` in this step** and link the diff.>

## 6. Requirement → design → test

| Requirement | Design element | Test(s) |
|---|---|---|
| FR-<AREA>-001 | <component / function> | <test class or E2E journey> |

## 7. Security, observability, performance

- **Threats and mitigations:** <…>
- **Logs and metrics:** <what shows this working or failing>
- **Performance and cost impact:** <…>

## 8. Risks and spikes

| Risk or unknown | Spike / mitigation | Time box | Fallback |
|---|---|---|---|
| | | | |

## 9. Decisions

<ADRs created or relied on.>

## 10. Complexity and exceptions

| Rule broken or complexity added | Why it's needed | Simpler option rejected because | Removal plan |
|---|---|---|---|
| | | | |
