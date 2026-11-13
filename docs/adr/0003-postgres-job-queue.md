# ADR-0003: PostgreSQL table as the job queue, with leases and fencing tokens

**Status:** Accepted · **Date:** 2026-11-13

## Context

Uploads must be accepted quickly (`202` in under 1 s) and processed in the background. The requirements are strict:

- an accepted upload is never lost, even if the host sleeps or crashes mid-job (NFR-REL-001);
- processing is effectively once: no double counting (NFR-REL-002);
- the free database sleeps after 5 minutes idle and has a monthly compute budget, so constant polling is not acceptable (NFR-COST-001).

## Decision

Use a **`jobs` table in PostgreSQL** as the queue:

1. **Enqueue in the same transaction** as the data that needs processing. Upload and job are committed together, or not at all.
2. **Claim** with `SELECT … FOR UPDATE SKIP LOCKED` in a short transaction. The claim increments `attempts` and writes a fresh random **lease token** (fencing token) and a **lease expiry**.
3. **Process** in one transaction. Inserts use `ON CONFLICT DO NOTHING`, and counters are updated only from rows actually inserted. The last statement marks the job `DONE` **only if the lease token still matches**. If it doesn't, the transaction rolls back.
4. **On error**, put the job back to `PENDING` with exponential backoff and jitter. When attempts run out, mark it `DEAD` and the upload `FAILED`, with a reason.
5. **After a crash or host sleep**, the lease expires and another worker reclaims the job.
6. **Wake up** on an in-process signal after commit, or at the next known due time (at most every 10 minutes). Never poll every second.
7. **Deduplicate** with a partial unique index on `dedupe_key` for jobs that are pending or running.

Full SQL is in PLAN §8.3.

## Consequences

- ➕ No extra infrastructure. The queue is backed up, consistent and inspectable with plain SQL.
- ➕ "Accept and enqueue" is atomic, so there are no orphaned uploads or ghost jobs.
- ➕ Fencing tokens make a stale worker harmless. It cannot commit after losing its lease.
- ➕ Idle means truly idle, so the database can sleep.
- ➖ Throughput is limited compared with a real broker. It is far beyond what this project needs (hundreds of jobs per minute is fine).
- ➖ A job may *run* more than once. Every handler must be idempotent, which is enforced by `ReprocessIsHarmlessIT`.

## Alternatives considered

- **Hold the row lock for the whole job** (claim and process in one transaction, no lease). Simpler, but after a process crash the attempt counter rolls back too, so a job that crashes the process would retry forever.
- **A message broker** (RabbitMQ, SQS, Kafka): another service to run and pay for, and accepting an upload would span two systems, needing an outbox anyway.
- **Spring `@Async` / in-memory queue:** loses jobs on restart or sleep. Violates NFR-REL-001.
- **A job library** (such as JobRunr or db-scheduler): reasonable, but hides the very mechanics this project wants to show and control (fencing, wake-up behaviour for a sleeping database).

## Revisit when

More than one instance runs permanently and job volume grows past thousands per minute, or jobs need to run longer than a lease (add lease renewal).
