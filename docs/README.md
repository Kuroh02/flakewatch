# Project documents

These are the documents the project was planned and built from. You don't need them to use
Flakewatch; start with the [README](../README.md) for that.

## Design

| Document | What's in it |
|---|---|
| [Detection rules](design/detection-rules.md) | Exactly how a test gets its label (Stable, Flaky, Broken…) |
| [Data model](design/data-model.md) | The database tables and why they look the way they do |
| [API contract](../api/openapi.yaml) | Every endpoint, request and response (OpenAPI) |
| [Decision records](adr/README.md) | The main technical decisions, each with the options considered |

## Planning

| Document | What's in it |
|---|---|
| [Spec](SPEC.md) | User stories, numbered requirements and success criteria |
| [Plan](PLAN.md) | Architecture, testing strategy, CI/CD and deployment in detail |
| [Tasks](TASKS.md) | The ordered build checklist, with what's done and what's left |
| [Constitution](CONSTITUTION.md) | The engineering rules the project follows |
| [Workflow](WORKFLOW.md) | How a change moves from idea to shipped code |
| [Glossary](GLOSSARY.md) | What each term means here |
| [Feature specs](../specs/README.md) | Specs for features after v1 |
| [Templates](templates/) | Templates for specs, plans, tasks and decision records |
| [Rules for AI assistants](../AGENTS.md) | How AI coding tools are expected to work in this repository |

## Guides

- [Upload from any CI](guides/upload-from-any-ci.md) (Jenkins, GitLab, or a plain `curl`)
- [Going live](GO-LIVE.md): deploying your own copy on free hosting

## Checking the documents

Every requirement in the spec has an ID (such as `FR-ING-004`). Tasks and tests refer to those IDs,
and a script checks the links between them. It runs in CI.

```bash
node scripts/check-docs.mjs            # requirements ↔ tasks, IDs and links
node scripts/check-docs.mjs --strict   # also requires a test for every M1/M2 requirement
```
