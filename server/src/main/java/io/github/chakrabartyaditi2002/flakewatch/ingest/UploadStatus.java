package io.github.chakrabartyaditi2002.flakewatch.ingest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Processing status of one upload (FR-ING-005). Matches {@code UploadStatus} in api/openapi.yaml. */
public record UploadStatus(
        long uploadId,
        long runId,
        String projectSlug,
        String jobKey,
        String variant,
        String status,
        Instant receivedAt,
        Instant processedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> summary,
        List<Map<String, Object>> warnings,
        String error) {}
