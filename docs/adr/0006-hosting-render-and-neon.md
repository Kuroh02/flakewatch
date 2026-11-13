# ADR-0006: Host on Render and Neon free tiers

**Status:** Accepted (confirm in spike S-3) · **Date:** 2026-11-13

## Context

The project needs a live link that stays up for months, at no cost, without a credit card if possible. Free tiers as checked in November 2026 (they change often, so recheck):

| Option | Free offer | Catch |
|---|---|---|
| Render web service | 512 MB, small CPU share, no card | Spins down after 15 minutes without traffic, about a minute to wake. Its free PostgreSQL expires after 30 days. |
| Koyeb | 512 MB, scales to zero | Card pre-authorisation required |
| Fly.io | No free tier for new customers (trial only) | Card required |
| Google Cloud Run | Generous always-free quota, scales to zero | Billing account (card) required |
| Oracle Cloud Always Free | Arm VMs, always on | Card and phone required; capacity errors are common |
| Neon PostgreSQL | 100 CU-hours per project per month, 1 GB storage per project | Compute suspends after 5 minutes idle; this can't be turned off |

## Decision

- **App:** Render free web service, deployed from a public GHCR image tagged by commit SHA.
- **Database:** Neon free project, in a region close to the Render region, using the direct (non-pooled) connection string.
- **Keep the image host-agnostic:** configured only by environment variables, with standard health endpoints. Moving to another host is a configuration change, not a code change.
- **Design around the limits:** a cold-start-tolerant uploader (120 s budget), no database polling (ADR-0003), idle connections closed, retention to stay under 1 GB, liveness checks that never touch the database.

## Spike S-3 results

*To be filled in during Phase 0:* memory at idle and under a 10,000-test upload, startup time with `--cpus 0.1`, the chosen `JAVA_TOOL_OPTIONS`.

## Consequences

- ➕ No cost and no card. Live and shareable.
- ➕ The constraints force good engineering (wake-up strategy, retention, memory tuning), which makes for good interview material.
- ➖ The first visit after idle takes about a minute. Mitigations: README and landing page explain it; the AOT cache experiment (T-7.2).
- ➖ Free tiers can change. The host-agnostic image limits the impact.

## Alternatives considered

The other rows in the table: each needs a card or has worse limits. **Kubernetes** in production was rejected: paid clusters cost money, and free ones aren't reliable. The manifests are kept as an optional, CI-tested showcase instead (PLAN §21.6).

## Revisit when

Cold starts prove unacceptable after T-7.2, a free tier changes, or a small budget becomes available (a small always-on instance would remove cold starts).
