package io.github.chakrabartyaditi2002.flakewatch.ingest;

import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import io.github.chakrabartyaditi2002.flakewatch.platform.json.Json;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads upload status for the status endpoint (FR-ING-005). */
@Component
public class UploadQueries {

    private final JdbcClient jdbc;

    public UploadQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @SuppressWarnings("unchecked")
    public Optional<UploadStatus> status(long projectId, long uploadId) {
        return jdbc.sql("""
                select u.id, u.run_id, p.slug, u.job_key, u.variant, u.status, u.received_at, u.processed_at,
                       u.summary::text as summary, u.warnings::text as warnings, u.error
                  from uploads u join projects p on p.id = u.project_id
                 where u.id = :id and u.project_id = :project
                """)
                .param("id", uploadId)
                .param("project", projectId)
                .query((rs, i) -> {
                    String summary = rs.getString("summary");
                    List<Object> warnings = Json.parseArray(rs.getString("warnings"));
                    return new UploadStatus(
                            rs.getLong("id"),
                            rs.getLong("run_id"),
                            rs.getString("slug"),
                            rs.getString("job_key"),
                            rs.getString("variant"),
                            rs.getString("status"),
                            Db.instant(rs, "received_at"),
                            Db.instant(rs, "processed_at"),
                            summary == null ? null : Json.parseObject(summary),
                            warnings.stream().map(w -> (Map<String, Object>) w).toList(),
                            rs.getString("error"));
                })
                .optional();
    }
}
