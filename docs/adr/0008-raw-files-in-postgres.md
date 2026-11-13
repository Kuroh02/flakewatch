# ADR-0008: Keep raw uploads in PostgreSQL for 7 days

**Status:** Accepted · **Date:** 2026-11-13

## Context

Uploaded report files are needed after the request ends: the background job parses them, and keeping them a few days allows reprocessing after a parser fix and debugging of odd reports (FR-ING-012). The usual home for files is object storage (S3, R2 and similar).

## Decision

Store raw files **gzip-compressed in `upload_files.content_gzip`** (PostgreSQL `bytea`), written in the same transaction as the upload and its job. A daily job sets the column to null after 7 days.

## Consequences

- ➕ Accepting an upload is one atomic transaction: files, upload and job together (ADR-0003). With object storage, a crash between "file stored" and "row written" would leave orphans, or need extra cleanup logic.
- ➕ No second service, credentials or SDK.
- ➕ Size is small: about 30 MB for 7 days at expected volume (data-model §6).
- ➖ Large `bytea` values make the database bigger and backups slower. Limited by the 7-day retention and the 20 MB per-file limit.
- ➖ Not suitable for heavy volumes.

## Alternatives considered

- **Object storage + outbox:** the right answer at scale. Unnecessary complexity now.
- **Don't keep raw files:** simplest, but loses the ability to reprocess after fixing a parser bug, which is likely early on.

## Revisit when

Raw uploads regularly exceed 50 MB per day, or database storage nears the free limit.
