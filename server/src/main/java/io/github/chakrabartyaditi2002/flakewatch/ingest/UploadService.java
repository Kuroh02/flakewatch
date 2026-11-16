package io.github.chakrabartyaditi2002.flakewatch.ingest;

import io.github.chakrabartyaditi2002.flakewatch.ingest.internal.ReceivedFile;
import io.github.chakrabartyaditi2002.flakewatch.ingest.internal.ReportFiles;
import io.github.chakrabartyaditi2002.flakewatch.jobs.DailyJobs;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobKinds;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobQueue;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobWakeup;
import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import io.github.chakrabartyaditi2002.flakewatch.platform.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Accepts an upload: saves it and its processing job in one transaction (Constitution Article 3,
 * data model §4). Either both exist, or neither does, so an accepted upload can't be lost.
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    /** What happened to an upload request. */
    public record Accepted(long uploadId, long runId, String status, boolean duplicate) {}

    private record RunRow(long id, String commitSha, String branch) {}

    private record ExistingUpload(long id, byte[] contentSha256, String status) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final JobQueue jobs;
    private final JobWakeup wakeup;
    private final DailyJobs dailyJobs;
    private final Clock clock;

    public UploadService(
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager,
            JobQueue jobs,
            JobWakeup wakeup,
            DailyJobs dailyJobs,
            Clock clock) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.jobs = jobs;
        this.wakeup = wakeup;
        this.dailyJobs = dailyJobs;
        this.clock = clock;
    }

    public Accepted accept(long projectId, UploadMetadata.Normalized metadata, List<ReceivedFile> files) {
        Instant now = clock.instant();
        byte[] contentHash = contentHash(metadata, files);
        Accepted accepted = tx.execute(status -> acceptInTransaction(projectId, metadata, files, contentHash, now));
        if (accepted != null && !accepted.duplicate()) {
            // after commit: wake a worker right away (PLAN §8.4)
            wakeup.signal();
            log.info("upload.accepted uploadId={} runId={} files={}", accepted.uploadId(), accepted.runId(), files.size());
        } else if (accepted != null) {
            log.info("upload.duplicate uploadId={}", accepted.uploadId());
        }
        try {
            dailyJobs.ensureToday();
        } catch (RuntimeException e) {
            log.warn("Could not queue today's daily jobs: {}", e.getMessage());
        }
        return accepted;
    }

    private Accepted acceptInTransaction(
            long projectId, UploadMetadata.Normalized m, List<ReceivedFile> files, byte[] contentHash, Instant now) {
        RunRow run = upsertRun(projectId, m, now);
        if (!run.commitSha().equals(m.commitSha()) || !run.branch().equals(m.branch())) {
            throw new ApiException(HttpStatus.CONFLICT, "run-metadata-mismatch",
                    "This CI run was already reported with commit " + run.commitSha() + " on branch " + run.branch());
        }

        long compressedBytes = 0;
        byte[][] compressed = new byte[files.size()][];
        for (int i = 0; i < files.size(); i++) {
            compressed[i] = ReportFiles.gzip(files.get(i).content());
            compressedBytes += compressed[i].length;
        }

        Optional<Long> inserted = jdbc.sql("""
                insert into uploads (project_id, run_id, job_key, variant, content_sha256, status, file_count,
                                     compressed_bytes, received_at)
                values (:project, :run, :jobKey, :variant, :hash, 'RECEIVED', :files, :bytes, :now)
                on conflict (run_id, job_key) do nothing
                returning id
                """)
                .param("project", projectId)
                .param("run", run.id())
                .param("jobKey", m.jobKey())
                .param("variant", m.variant())
                .param("hash", contentHash)
                .param("files", files.size())
                .param("bytes", compressedBytes)
                .param("now", Db.ts(now))
                .query(Long.class)
                .optional();

        if (inserted.isEmpty()) {
            // A repeat of an earlier upload (FR-ING-004)
            ExistingUpload existing = jdbc.sql("""
                    select id, content_sha256, status from uploads where run_id = :run and job_key = :jobKey
                    """)
                    .param("run", run.id())
                    .param("jobKey", m.jobKey())
                    .query((rs, i) -> new ExistingUpload(rs.getLong("id"), rs.getBytes("content_sha256"), rs.getString("status")))
                    .single();
            if (!Arrays.equals(existing.contentSha256(), contentHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "duplicate-upload-different-content",
                        "This run, attempt and job key were already uploaded with different content");
            }
            return new Accepted(existing.id(), run.id(), existing.status(), true);
        }

        long uploadId = inserted.get();
        for (int i = 0; i < files.size(); i++) {
            jdbc.sql("""
                    insert into upload_files (upload_id, file_name, content_gzip, original_bytes)
                    values (:upload, :name, :content, :bytes)
                    """)
                    .param("upload", uploadId)
                    .param("name", files.get(i).name())
                    .param("content", compressed[i])
                    .param("bytes", (long) files.get(i).content().length)
                    .update();
        }
        jobs.enqueue(JobKinds.PROCESS_UPLOAD, Map.of("uploadId", uploadId), "upload:" + uploadId, now);
        return new Accepted(uploadId, run.id(), "RECEIVED", false);
    }

    /** Finds or creates the CI run. Shards of one run share it (FR-ING-011). */
    private RunRow upsertRun(long projectId, UploadMetadata.Normalized m, Instant now) {
        jdbc.sql("""
                insert into ci_runs (project_id, provider, external_run_id, attempt, commit_sha, branch, pull_request,
                                     repository_url, started_at, created_at)
                values (:project, :provider, :runId, :attempt, :commit, :branch, :pr, :repo, :startedAt, :now)
                on conflict (project_id, provider, external_run_id, attempt) do nothing
                """)
                .param("project", projectId)
                .param("provider", m.provider())
                .param("runId", m.runId())
                .param("attempt", m.runAttempt())
                .param("commit", m.commitSha())
                .param("branch", m.branch())
                .param("pr", m.pullRequest())
                .param("repo", m.repositoryUrl())
                .param("startedAt", Db.ts(m.runStartedAt() == null ? now : m.runStartedAt()))
                .param("now", Db.ts(now))
                .update();
        return jdbc.sql("""
                select id, commit_sha, branch from ci_runs
                 where project_id = :project and provider = :provider and external_run_id = :runId and attempt = :attempt
                """)
                .param("project", projectId)
                .param("provider", m.provider())
                .param("runId", m.runId())
                .param("attempt", m.runAttempt())
                .query((rs, i) -> new RunRow(rs.getLong("id"), rs.getString("commit_sha"), rs.getString("branch")))
                .single();
    }

    /**
     * SHA-256 over the canonical metadata plus {@code name:sha256(decompressed content)} for each
     * file in name order. Re-compressing the same file doesn't change it (PLAN §7.2 step 5).
     */
    static byte[] contentHash(UploadMetadata.Normalized metadata, List<ReceivedFile> files) {
        StringBuilder sb = new StringBuilder(metadata.canonical());
        files.stream()
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .forEach(f -> sb.append('\n').append(f.name()).append(':').append(f.sha256()));
        try {
            return MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
