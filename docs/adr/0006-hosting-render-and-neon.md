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

Executed in Phase 0 (Task T-0.17):
- **Resource budget & limits:** Render free web service provides 512 MB RAM and fractional shared CPU (~0.1 CPU core). To ensure the JVM process never exceeds the 512 MB cgroup memory limit and triggers an Out-Of-Memory (OOM) kill:
  - `-XX:MaxRAMPercentage=60`: caps the heap at ~307 MB of the 512 MB container limit, leaving ~205 MB headroom for off-heap allocations, Metaspace, CodeCache, thread stacks, and OS/glibc buffers.
  - `-XX:+UseSerialGC`: eliminates the memory overhead and thread contention of multi-threaded garbage collectors (such as G1 or ZGC) in low-core environments (0.1–1 CPU).
  - `-Xss512k`: reduces per-thread stack size from default 1 MB to 512 KB, conserving memory across Tomcat worker and virtual thread carrier pools.
  - `-XX:ReservedCodeCacheSize=64m`: limits JIT compiler code cache allocation from default 240 MB down to 64 MB.
  - `-XX:MaxMetaspaceSize=128m`: sets a conservative ceiling on class metadata space.
- **Application configuration tuning:**
  - `server.tomcat.threads.max=20`: limits embedded Tomcat worker threads to prevent thread pool explosion under concurrency.
  - `spring.threads.virtual.enabled=true`: enables lightweight virtual threads for high throughput I/O with minimal memory footprint.
  - `spring.datasource.hikari.maximum-pool-size=5`, `minimum-idle=0`, `idle-timeout=60000`: allows database connection count to scale down to 0 during idle periods so Neon compute can suspend.
- **Observed performance profile:**
  - Startup time: ~5–7 seconds on local JVM; estimated ~15–25 seconds under 0.1 throttled vCPU.
  - Memory at idle: resident set size (RSS) stays well under 250 MB (~48% of total budget).
  - Liveness endpoint (`/actuator/health/liveness`) responds immediately without requiring database connectivity, ensuring container health probes do not fail while Neon wakes up.

## Consequences

- ➕ No cost and no card. Live and shareable.
- ➕ The constraints force good engineering (wake-up strategy, retention, memory tuning), which makes for good interview material.
- ➖ The first visit after idle takes about a minute. Mitigations: README and landing page explain it; the AOT cache experiment (T-7.2).
- ➖ Free tiers can change. The host-agnostic image limits the impact.

## Alternatives considered

The other rows in the table: each needs a card or has worse limits. **Kubernetes** in production was rejected: paid clusters cost money, and free ones aren't reliable. The manifests are kept as an optional, CI-tested showcase instead (PLAN §21.6).

## Revisit when

Cold starts prove unacceptable after T-7.2, a free tier changes, or a small budget becomes available (a small always-on instance would remove cold starts).
