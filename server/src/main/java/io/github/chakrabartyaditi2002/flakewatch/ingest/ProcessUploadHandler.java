package io.github.chakrabartyaditi2002.flakewatch.ingest;

import io.github.chakrabartyaditi2002.flakewatch.core.fingerprint.Fingerprint;
import io.github.chakrabartyaditi2002.flakewatch.core.fingerprint.Fingerprinter;
import io.github.chakrabartyaditi2002.flakewatch.core.report.ExecutionStatus;
import io.github.chakrabartyaditi2002.flakewatch.core.report.ParseResult;
import io.github.chakrabartyaditi2002.flakewatch.core.report.ParseWarning;
import io.github.chakrabartyaditi2002.flakewatch.core.report.ParsedExecution;
import io.github.chakrabartyaditi2002.flakewatch.core.report.ParsedTestCase;
import io.github.chakrabartyaditi2002.flakewatch.core.report.ReportFile;
import io.github.chakrabartyaditi2002.flakewatch.core.report.ReportParseException;
import io.github.chakrabartyaditi2002.flakewatch.core.report.ReportParser;
import io.github.chakrabartyaditi2002.flakewatch.core.testkey.TestKeys;
import io.github.chakrabartyaditi2002.flakewatch.detection.DetectionService;
import io.github.chakrabartyaditi2002.flakewatch.ingest.internal.ReportFiles;
import io.github.chakrabartyaditi2002.flakewatch.jobs.Job;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobHandler;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobKinds;
import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import io.github.chakrabartyaditi2002.flakewatch.platform.db.SqlArray;
import io.github.chakrabartyaditi2002.flakewatch.platform.json.Json;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Parses an upload and stores its results (PLAN §7.4). Runs inside the job's transaction.
 *
 * <p>Effectively-once (NFR-REL-002): executions are inserted with {@code on conflict do nothing},
 * and statistics and failure-group counts are added only for rows that were actually inserted.
 * Running the same job twice therefore changes nothing the second time.
 */
@Component
public class ProcessUploadHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(ProcessUploadHandler.class);
    static final int BATCH = 1000;

    private final JdbcClient jdbc;
    private final DetectionService detection;
    private final Clock clock;
    private final ReportParser parser = new ReportParser();

    public ProcessUploadHandler(JdbcClient jdbc, DetectionService detection, Clock clock) {
        this.jdbc = jdbc;
        this.detection = detection;
        this.clock = clock;
    }

    @Override
    public String kind() {
        return JobKinds.PROCESS_UPLOAD;
    }

    private record UploadRow(
            long id, long projectId, long runId, String variant, String branch, String defaultBranch, Instant executedAt) {}

    private record Inserted(long testCaseId, int index, boolean isFinal, String status, int durationMs, Long groupId) {}

    @Override
    public void handle(Job job) {
        long uploadId = Json.asLong(job.payload().get("uploadId"));
        UploadRow upload = jdbc.sql("""
                select u.id, u.project_id, u.run_id, u.variant, r.branch, p.default_branch, r.started_at
                  from uploads u
                  join ci_runs r on r.id = u.run_id
                  join projects p on p.id = u.project_id
                 where u.id = :id
                """)
                .param("id", uploadId)
                .query((rs, i) -> new UploadRow(
                        rs.getLong("id"),
                        rs.getLong("project_id"),
                        rs.getLong("run_id"),
                        rs.getString("variant"),
                        rs.getString("branch"),
                        rs.getString("default_branch"),
                        Db.instant(rs, "started_at")))
                .optional()
                .orElse(null);
        if (upload == null) {
            log.warn("upload {} no longer exists (project deleted?); nothing to do", uploadId);
            return;
        }

        List<ReportFile> files = loadFiles(uploadId);
        ParseResult parsed;
        try {
            parsed = parser.parse(files);
        } catch (ReportParseException e) {
            // A permanent problem with the files: report it to the user, don't retry (FR-ING-008)
            markFailed(uploadId, e.getMessage());
            log.info("upload.failed uploadId={} reason={}", uploadId, e.getMessage());
            return;
        }

        List<ParsedTestCase> tests = mergeByTestKey(parsed.testCases());
        Set<Long> failedTestIds = new LinkedHashSet<>();
        Set<Long> allTestIds = new LinkedHashSet<>();
        for (int from = 0; from < tests.size(); from += BATCH) {
            List<ParsedTestCase> batch = tests.subList(from, Math.min(tests.size(), from + BATCH));
            storeBatch(upload, batch, allTestIds, failedTestIds);
        }

        Instant now = clock.instant();
        jdbc.sql("""
                update uploads set status = 'PROCESSED', processed_at = :now, error = null,
                       summary = cast(:summary as jsonb), warnings = cast(:warnings as jsonb)
                 where id = :id
                """)
                .param("now", Db.ts(now))
                .param("summary", Json.write(summary(tests)))
                .param("warnings", Json.write(warnings(parsed.warnings())))
                .param("id", uploadId)
                .update();

        detection.queueReclassification(upload.projectId(), allTestIds, failedTestIds, now);
        log.info("upload.processed uploadId={} tests={} warnings={}", uploadId, tests.size(), parsed.warnings().size());
    }

    @Override
    public void onDead(Job job, String error) {
        Object id = job.payload().get("uploadId");
        if (id != null) {
            markFailed(Json.asLong(id), "Processing failed after several attempts: " + error);
        }
    }

    private void markFailed(long uploadId, String reason) {
        jdbc.sql("update uploads set status = 'FAILED', error = :error, processed_at = :now where id = :id")
                .param("error", reason)
                .param("now", Db.ts(clock.instant()))
                .param("id", uploadId)
                .update();
    }

    private List<ReportFile> loadFiles(long uploadId) {
        record Row(String name, byte[] gzip) {}
        List<Row> rows = jdbc.sql("select file_name, content_gzip from upload_files where upload_id = :id order by file_name")
                .param("id", uploadId)
                .query((rs, i) -> new Row(rs.getString("file_name"), rs.getBytes("content_gzip")))
                .list();
        List<ReportFile> files = new ArrayList<>();
        for (Row row : rows) {
            if (row.gzip() == null) {
                throw new ReportParseException(row.name(), null, "the raw file was already deleted by retention");
            }
            files.add(new ReportFile(row.name(), ReportFiles.gunzip(row.gzip(), ReportFiles.MAX_DECOMPRESSED_FILE_BYTES)));
        }
        return files;
    }

    /** Two different suite/name pairs can build the same test key; treat them as one test case. */
    static List<ParsedTestCase> mergeByTestKey(List<ParsedTestCase> tests) {
        Map<String, ParsedTestCase> byKey = new LinkedHashMap<>();
        for (ParsedTestCase t : tests) {
            String key = TestKeys.testKey(t.suite(), t.name());
            ParsedTestCase existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, t);
            } else {
                List<ParsedExecution> merged = new ArrayList<>(existing.executions());
                for (ParsedExecution e : t.executions()) {
                    merged.add(e.withIndex(merged.size()));
                }
                byKey.put(key, new ParsedTestCase(existing.suite(), existing.name(), existing.file(), merged));
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private void storeBatch(UploadRow upload, List<ParsedTestCase> batch, Set<Long> allTestIds, Set<Long> failedTestIds) {
        // 1. Upsert test cases
        int n = batch.size();
        String[] hashes = new String[n];
        String[] keys = new String[n];
        String[] methodKeys = new String[n];
        String[] suites = new String[n];
        String[] names = new String[n];
        String[] fileNames = new String[n];
        for (int i = 0; i < n; i++) {
            ParsedTestCase t = batch.get(i);
            keys[i] = TestKeys.testKey(t.suite(), t.name());
            hashes[i] = HexFormat.of().formatHex(TestKeys.keyHash(keys[i]));
            methodKeys[i] = TestKeys.methodKey(t.suite(), t.name());
            suites[i] = t.suite();
            names[i] = t.name();
            fileNames[i] = t.file();
        }
        Map<String, Long> idByHash = new HashMap<>();
        jdbc.sql("""
                insert into test_cases (project_id, key_hash, test_key, method_key, suite, name, file, first_seen_at, last_seen_at)
                select :project, decode(t.h, 'hex'), t.k, t.m, t.s, t.n, t.f, :at, :at
                  from unnest(:h, :k, :m, :s, :n, :f) as t(h, k, m, s, n, f)
                on conflict (project_id, key_hash) do update
                   set last_seen_at = greatest(test_cases.last_seen_at, excluded.last_seen_at),
                       first_seen_at = least(test_cases.first_seen_at, excluded.first_seen_at),
                       file = coalesce(test_cases.file, excluded.file)
                returning id, encode(key_hash, 'hex') as h
                """)
                .param("project", upload.projectId())
                .param("at", Db.ts(upload.executedAt()))
                .param("h", SqlArray.of("text", (Object[]) hashes))
                .param("k", SqlArray.of("text", (Object[]) keys))
                .param("m", SqlArray.of("text", (Object[]) methodKeys))
                .param("s", SqlArray.of("text", (Object[]) suites))
                .param("n", SqlArray.of("text", (Object[]) names))
                .param("f", SqlArray.of("text", (Object[]) fileNames))
                .query((rs, i) -> Map.entry(rs.getString("h"), rs.getLong("id")))
                .list()
                .forEach(e -> idByHash.put(e.getKey(), e.getValue()));

        // 2. Upsert failure groups, one per distinct fingerprint
        Map<String, Long> groupByFingerprint = new HashMap<>();
        for (ParsedTestCase t : batch) {
            for (ParsedExecution e : t.executions()) {
                if (e.status().isFailure()) {
                    Fingerprint fp = Fingerprinter.fingerprint(e.failureType(), e.message(), e.stackTrace());
                    if (!groupByFingerprint.containsKey(fp.hex())) {
                        groupByFingerprint.put(fp.hex(), upsertGroup(upload, fp, e.message()));
                    }
                }
            }
        }

        // 3. Insert executions; only rows actually inserted come back
        List<Long> tc = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        List<Boolean> fin = new ArrayList<>();
        List<String> st = new ArrayList<>();
        List<Boolean> q = new ArrayList<>();
        List<Integer> dur = new ArrayList<>();
        List<Long> grp = new ArrayList<>();
        List<String> ft = new ArrayList<>();
        List<String> fm = new ArrayList<>();
        List<String> stack = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ParsedTestCase t = batch.get(i);
            long testCaseId = idByHash.get(hashes[i]);
            allTestIds.add(testCaseId);
            for (ParsedExecution e : t.executions()) {
                boolean failure = e.status().isFailure();
                if (failure) {
                    failedTestIds.add(testCaseId);
                }
                tc.add(testCaseId);
                idx.add(e.index());
                fin.add(e.index() == t.executions().size() - 1);
                st.add(e.status().name());
                q.add(e.quarantined());
                dur.add((int) Math.min(Integer.MAX_VALUE, e.durationMs()));
                grp.add(failure
                        ? groupByFingerprint.get(Fingerprinter.fingerprint(e.failureType(), e.message(), e.stackTrace()).hex())
                        : null);
                ft.add(failure ? e.failureType() : null);
                fm.add(failure ? e.message() : null);
                stack.add(failure ? e.stackTrace() : null);
            }
        }
        List<Inserted> inserted = jdbc.sql("""
                insert into test_executions (project_id, test_case_id, upload_id, run_id, execution_index, is_final, status,
                                             quarantined, duration_ms, executed_at, failure_group_id, failure_type,
                                             failure_message, stack_trace)
                select :project, t.tc, :upload, :run, t.idx, t.fin, t.st, t.q, t.dur, :at, t.grp, t.ft, t.fm, t.stack
                  from unnest(:tc, :idx, :fin, :st, :q, :dur, :grp, :ft, :fm, :stack)
                       as t(tc, idx, fin, st, q, dur, grp, ft, fm, stack)
                on conflict (upload_id, test_case_id, execution_index) do nothing
                returning test_case_id, execution_index, is_final, status, duration_ms, failure_group_id
                """)
                .param("project", upload.projectId())
                .param("upload", upload.id())
                .param("run", upload.runId())
                .param("at", Db.ts(upload.executedAt()))
                .param("tc", SqlArray.of("bigint", tc.toArray()))
                .param("idx", SqlArray.of("integer", idx.toArray()))
                .param("fin", SqlArray.of("boolean", fin.toArray()))
                .param("st", SqlArray.of("text", st.toArray()))
                .param("q", SqlArray.of("boolean", q.toArray()))
                .param("dur", SqlArray.of("integer", dur.toArray()))
                .param("grp", SqlArray.of("bigint", grp.toArray()))
                .param("ft", SqlArray.of("text", ft.toArray()))
                .param("fm", SqlArray.of("text", fm.toArray()))
                .param("stack", SqlArray.of("text", stack.toArray()))
                .query((rs, i) -> new Inserted(
                        rs.getLong("test_case_id"),
                        rs.getInt("execution_index"),
                        rs.getBoolean("is_final"),
                        rs.getString("status"),
                        rs.getInt("duration_ms"),
                        Db.nullableLong(rs, "failure_group_id")))
                .list();
        if (inserted.isEmpty()) {
            return;
        }

        // 4. Statistics and group counts, from inserted rows only
        addDailyStats(upload, inserted);
        Map<Long, Long> occurrences = new HashMap<>();
        for (Inserted row : inserted) {
            if (row.groupId() != null) {
                occurrences.merge(row.groupId(), 1L, Long::sum);
            }
        }
        for (Map.Entry<Long, Long> e : occurrences.entrySet()) {
            jdbc.sql("update failure_groups set occurrences = occurrences + :n where id = :id")
                    .param("n", e.getValue())
                    .param("id", e.getKey())
                    .update();
        }
    }

    private long upsertGroup(UploadRow upload, Fingerprint fp, String message) {
        String sample = message == null ? "" : message.length() > 4096 ? message.substring(0, 4096) : message;
        return jdbc.sql("""
                insert into failure_groups (project_id, fingerprint, failure_type, normalized_message, sample_message,
                                            top_frames, first_seen_at, last_seen_at, occurrences)
                values (:project, decode(:fp, 'hex'), :type, :normalized, :sample, :frames, :at, :at, 0)
                on conflict (project_id, fingerprint) do update
                   set last_seen_at = greatest(failure_groups.last_seen_at, excluded.last_seen_at),
                       first_seen_at = least(failure_groups.first_seen_at, excluded.first_seen_at)
                returning id
                """)
                .param("project", upload.projectId())
                .param("fp", fp.hex())
                .param("type", fp.failureType())
                .param("normalized", fp.normalizedMessage())
                .param("sample", sample)
                .param("frames", SqlArray.of("text", fp.topFrames().toArray()))
                .param("at", Db.ts(upload.executedAt()))
                .query(Long.class)
                .single();
    }

    private void addDailyStats(UploadRow upload, List<Inserted> inserted) {
        record Totals(int[] counts, long[] duration) {}
        Map<Long, Totals> byTest = new LinkedHashMap<>();
        for (Inserted row : inserted) {
            Totals t = byTest.computeIfAbsent(row.testCaseId(), id -> new Totals(new int[4], new long[1]));
            t.duration()[0] += row.durationMs();
            if (row.isFinal()) {
                switch (ExecutionStatus.valueOf(row.status())) {
                    case PASSED -> t.counts()[0]++;
                    case FAILED, ERRORED -> t.counts()[1]++;
                    case SKIPPED -> t.counts()[2]++;
                }
            } else if (ExecutionStatus.valueOf(row.status()).isFailure()) {
                t.counts()[3] = 1;
            }
        }
        int size = byTest.size();
        Object[] ids = new Object[size];
        Object[] passed = new Object[size];
        Object[] failed = new Object[size];
        Object[] skipped = new Object[size];
        Object[] retried = new Object[size];
        Object[] duration = new Object[size];
        int i = 0;
        for (Map.Entry<Long, Totals> e : byTest.entrySet()) {
            ids[i] = e.getKey();
            passed[i] = e.getValue().counts()[0];
            failed[i] = e.getValue().counts()[1];
            skipped[i] = e.getValue().counts()[2];
            retried[i] = e.getValue().counts()[3];
            duration[i] = e.getValue().duration()[0];
            i++;
        }
        jdbc.sql("""
                insert into test_daily_stats (test_case_id, project_id, day, default_branch, passed, failed, skipped,
                                              retried, total_duration_ms)
                select t.tc, :project, :day, :defaultBranch, t.p, t.f, t.s, t.r, t.d
                  from unnest(:tc, :p, :f, :s, :r, :d) as t(tc, p, f, s, r, d)
                on conflict (test_case_id, day, default_branch) do update
                   set passed = test_daily_stats.passed + excluded.passed,
                       failed = test_daily_stats.failed + excluded.failed,
                       skipped = test_daily_stats.skipped + excluded.skipped,
                       retried = test_daily_stats.retried + excluded.retried,
                       total_duration_ms = test_daily_stats.total_duration_ms + excluded.total_duration_ms
                """)
                .param("project", upload.projectId())
                .param("day", LocalDate.ofInstant(upload.executedAt(), ZoneOffset.UTC))
                .param("defaultBranch", upload.branch().equals(upload.defaultBranch()))
                .param("tc", SqlArray.of("bigint", ids))
                .param("p", SqlArray.of("integer", passed))
                .param("f", SqlArray.of("integer", failed))
                .param("s", SqlArray.of("integer", skipped))
                .param("r", SqlArray.of("integer", retried))
                .param("d", SqlArray.of("bigint", duration))
                .update();
    }

    /** {tests, passed, failed, errored, skipped, retried} by final outcome (UploadSummary). */
    static Map<String, Object> summary(List<ParsedTestCase> tests) {
        int passed = 0;
        int failed = 0;
        int errored = 0;
        int skipped = 0;
        int retried = 0;
        for (ParsedTestCase t : tests) {
            switch (t.finalExecution().status()) {
                case PASSED -> passed++;
                case FAILED -> failed++;
                case ERRORED -> errored++;
                case SKIPPED -> skipped++;
            }
            if (t.wasRetried()) {
                retried++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tests", tests.size());
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("errored", errored);
        summary.put("skipped", skipped);
        summary.put("retried", retried);
        return summary;
    }

    private static List<Map<String, Object>> warnings(List<ParseWarning> warnings) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ParseWarning w : warnings) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("file", w.file());
            m.put("line", w.line());
            m.put("message", w.message());
            out.add(m);
        }
        return out;
    }
}
