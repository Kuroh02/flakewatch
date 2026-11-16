package io.github.chakrabartyaditi2002.flakewatch.ingest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * The {@code metadata} part of an upload (FR-ING-002). Matches {@code UploadMetadata} in api/openapi.yaml.
 * Optional fields get their defaults in {@link #normalized()}.
 */
public record UploadMetadata(
        @NotBlank @Pattern(regexp = "^[0-9a-f]{7,64}$", message = "must be 7 to 64 lowercase hex characters")
                String commitSha,
        @NotBlank @Size(max = 255) String branch,
        @Pattern(regexp = "^(GITHUB|JENKINS|GITLAB|OTHER)$", message = "must be GITHUB, JENKINS, GITLAB or OTHER")
                String provider,
        @NotBlank @Size(max = 100) String runId,
        @Min(1) Integer runAttempt,
        @Size(min = 1, max = 100) String jobKey,
        @Size(max = 100) String variant,
        @Min(1) Integer pullRequest,
        Instant runStartedAt,
        @Size(max = 500) String repositoryUrl) {

    /** The metadata with defaults filled in. */
    public record Normalized(
            String commitSha,
            String branch,
            String provider,
            String runId,
            int runAttempt,
            String jobKey,
            String variant,
            Integer pullRequest,
            Instant runStartedAt,
            String repositoryUrl) {

        /** A fixed, ordered text form used in the upload's content hash (PLAN §7.2 step 5). */
        public String canonical() {
            return String.join("\n",
                    "commitSha=" + commitSha,
                    "branch=" + branch,
                    "provider=" + provider,
                    "runId=" + runId,
                    "runAttempt=" + runAttempt,
                    "jobKey=" + jobKey,
                    "variant=" + variant,
                    "pullRequest=" + (pullRequest == null ? "" : pullRequest),
                    "runStartedAt=" + (runStartedAt == null ? "" : runStartedAt),
                    "repositoryUrl=" + (repositoryUrl == null ? "" : repositoryUrl));
        }
    }

    public Normalized normalized() {
        return new Normalized(
                commitSha,
                branch.strip(),
                provider == null ? "OTHER" : provider,
                runId.strip(),
                runAttempt == null ? 1 : runAttempt,
                jobKey == null || jobKey.isBlank() ? "default" : jobKey.strip(),
                variant == null ? "" : variant.strip(),
                pullRequest,
                runStartedAt,
                repositoryUrl == null || repositoryUrl.isBlank() ? null : repositoryUrl.strip());
    }
}
