# ADR-0002: One service with a pure-Java core

**Status:** Accepted · **Date:** 2026-11-13

## Context

Flakewatch has a few clear areas: ingestion, detection, failure grouping, projects and access, quarantine, and the dashboard. It's tempting to split them into microservices. But:

- it's built and run by one person, on free hosting with 512 MB of RAM;
- every area reads and writes the same data, often in the same transaction (for example, accept an upload *and* queue its job);
- the hardest logic (parsing, detection, fingerprinting) needs to be tested very thoroughly: property tests, mutation testing, thousands of generated cases.

## Decision

1. Build **one deployable Spring Boot service** (`server`) that also serves the React build.
2. Organise `server` **by feature** (`ingest`, `detection`, `projects`, …). Each feature has a small public API and an `internal` package.
3. Put the hard logic in a separate Maven module, **`core`**: plain Java with no Spring, no database and no I/O beyond a given input stream. This is the "functional core, imperative shell" pattern.
4. Enforce the boundaries with **ArchUnit** tests (PLAN §6.3).

## Consequences

- ➕ One thing to build, deploy, monitor and explain. It fits the free tier.
- ➕ Cross-feature transactions are just database transactions. No distributed-consistency problems.
- ➕ `core` tests are fast (milliseconds, no Spring context), so property tests and PIT are practical.
- ➕ Feature boundaries are real (tested), so splitting out a service later would be straightforward.
- ➖ The whole app scales as one unit. That's fine at this scale (see the size estimates in the data model).
- ➖ It needs discipline to keep features apart. ArchUnit catches mistakes.

## Alternatives considered

- **Microservices** (for example ingestion, detection and API as separate services with a broker): more infrastructure, more failure modes and more cost, with no requirement that needs it. Rejected under Constitution Article 4.
- **A single module without a separate `core`:** simpler build, but the domain logic would sit near Spring and the database, making tests slower and boundaries easier to blur.

## Revisit when

Ingestion load needs to scale separately from the dashboard, or a second developer team owns a part of the system.
