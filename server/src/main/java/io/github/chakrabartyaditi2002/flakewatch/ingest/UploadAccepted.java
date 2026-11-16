package io.github.chakrabartyaditi2002.flakewatch.ingest;

/** Reply to an upload (FR-ING-003). Matches {@code UploadAccepted} in api/openapi.yaml. */
public record UploadAccepted(
        long uploadId, long runId, String status, String statusUrl, String runUrl, boolean duplicate) {}
