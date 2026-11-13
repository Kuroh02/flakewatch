# ADR-0001: Record architecture decisions

**Status:** Accepted · **Date:** 2026-11-13

## Context

This project is built by one person over several months and will be read by others (reviewers, interviewers, future contributors). Decisions made early will be questioned later, often by the same person who made them. Without a record, the reasoning is lost and old debates get re-argued.

## Decision

Record significant decisions as short Markdown files in `docs/adr/`, numbered in order, using [the template](../templates/adr-template.md):

- one decision per file;
- statuses: Proposed → Accepted → (Superseded by ADR-XXXX | Deprecated);
- accepted ADRs are not rewritten; a new ADR supersedes an old one;
- plans list the ADRs they create or rely on.

## Consequences

- ➕ Reasoning survives. New readers can see why, not just what.
- ➕ Writing the trade-offs down forces clearer decisions.
- ➖ A little extra writing for each significant decision. Kept short on purpose: about one page.

## Alternatives considered

- **Comments in code:** too scattered, and they can't explain options that were rejected.
- **A wiki:** drifts away from the code and isn't reviewed in pull requests.
