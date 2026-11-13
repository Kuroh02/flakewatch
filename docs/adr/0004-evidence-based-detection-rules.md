# ADR-0004: Evidence-based detection rules

**Status:** Accepted · **Date:** 2026-11-13

## Context

The core promise of the product is telling flaky failures apart from real ones, and being trusted while doing it (Constitution P1). Common approaches:

- **Failure rate** ("fails more than X% of the time"): confuses "broken for 3 days, then fixed" with "randomly unreliable".
- **Flip rate with a threshold** ("result changes more than X% of the time"): depends heavily on the number of runs. One failure in 10 runs scores 0.22; in 12 runs, 0.18.
- **Statistical models:** hard to explain, hard to test, and they still need a threshold.

## Decision

Use **explicit rules in a fixed order**, defined in [detection-rules.md](../design/detection-rules.md):

1. **Flaky requires evidence:** a *retry pass* within one upload, or *mixed results on the same commit and variant*. Both are proof that identical code gave different results.
2. Softer labels for everything else: **Broken** (last 3 default-branch results failed), **Failing** (latest failed), **Suspected** (failed and recovered on its own at least twice), **Not enough data**, **Stable**.
3. **Judge each variant separately**, then combine them in a fixed attention order.
4. **Flip rate and impact** are for ranking and display only.
5. The classifier is a **pure function**, specified with worked examples and properties that are tested directly.

## Consequences

- ➕ Every Flaky label can be explained in one sentence, with links to proof.
- ➕ Clean to test: worked examples become parameterised tests; invariants become property tests.
- ➕ Variant-specific flakiness ("only on Firefox") shows up instead of being hidden.
- ➖ Teams that never retry and never re-run the same commit only get *Suspected*, never *Flaky*. That's honest: without proof we shouldn't claim more. The docs recommend enabling one retry (for example Surefire `rerunFailingTestsCount=1`).
- ➖ Rules use fixed defaults that may not suit every project. Owners can adjust the window, recoveries and broken streak within limits (FR-DET-011).

## Alternatives considered

The three approaches in *Context*. Each was rejected for the reason stated there.

## Revisit when

Real-world data from several projects shows a common pattern these rules misclassify.
