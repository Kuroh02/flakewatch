# ADR-0005: In-house property-testing helper instead of jqwik

**Status:** Accepted (confirmed in spike S-2) · **Date:** 2026-11-14

## Context

Property-based tests are central to this project's testing story (Constitution Article 2): detection, fingerprinting and parsing all have invariants that example tests can't cover well. The usual Java choice is jqwik. As of November 2026:

- jqwik is in **maintenance mode** (no new features without funding).
- Its current releases are built on **JUnit Platform 1.x**, and its maintainers say future releases, if any, would move to JUnit Platform 6. **Spring Boot 4 uses JUnit 6.** Mixing the two platforms in one test run is fragile.
- From version 1.10, jqwik ships an **"Anti-AI Usage Clause"** that strongly discourages use with AI coding agents, and its output may confuse such agents. This project uses AI assistants under the rules in `AGENTS.md`.
- Other Java options are older or unmaintained, or don't integrate natively with JUnit 6.

## Decision

Write a small **`Props` helper** (about 200 lines) on plain JUnit 6, in `core`'s test sources, shared through a Maven `test-jar`:

- **Generators** are composable functions of a seeded `SplittableRandom`.
- **`Props.forAll(gen, cases, property)`** runs a property N times (default 500; nightly 10,000).
- **The seed** comes from `-Dprops.seed`, or is random and printed. Every failure prints the seed, the case number and the input, so it can be replayed exactly.
- **Shrinking-lite** for lists: on failure, remove elements one at a time while the property still fails, and report the smallest failing input.
- The helper has **its own tests** (same seed gives the same cases; shrinking finds the minimal list in known examples).

## Consequences

- ➕ Works natively with JUnit 6. No platform conflicts, and no usage restrictions.
- ➕ A small, understandable piece of testing infrastructure, which is a good engineering talking point.
- ➖ Less powerful shrinking than a mature library: only list shrinking; other shapes print as generated. Acceptable, because our inputs are mostly lists (histories, executions).
- ➖ The helper is ours to maintain. Kept deliberately small.

## Outcome of Spike S-2

Confirmed in Spike S-2 (Task T-0.16):
- Implemented `Generator`, `Gens`, `Props`, and `PropertyAssertionError` in `core` test sources (~200 lines).
- `PropsTest` verified:
  1. Identical generation sequences across runs given the same seed.
  2. Execution of property tests with configurable `-Dprops.cases`.
  3. Seed logging and exact failure replay via `-Dprops.seed=<seed>`.
  4. List shrinking-lite successfully isolates the minimal failing subset.
- Result: Helper is accepted and established for all property tests in Flakewatch.

## Alternatives considered

- **jqwik 1.9.3 pinned in `core` on JUnit Jupiter 5.x:** `core` has no Spring, so it could use its own JUnit version. This is the **fallback** if spike S-2 shows the helper is not enough.
- **Example tests only:** misses edge cases. Rejected under Constitution Article 2.

## Revisit when

A maintained property-testing library with native JUnit 6 support becomes available.
