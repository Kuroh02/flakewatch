package io.github.chakrabartyaditi2002.flakewatch.projects.internal;

import io.github.chakrabartyaditi2002.flakewatch.core.detection.DetectionSettings;
import io.github.chakrabartyaditi2002.flakewatch.platform.db.Db;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {

    /** Settings are stored as overrides; missing or out-of-range values fall back to the defaults. */
    private static final String SELECT = """
            select p.id, p.slug, p.name, p.visibility, p.default_branch, p.created_at, u.login as owner_login,
                   (p.settings->>'windowDays')::int as window_days,
                   (p.settings->>'suspectedRecoveries')::int as suspected_recoveries,
                   (p.settings->>'brokenStreak')::int as broken_streak
              from projects p
              left join users u on u.id = p.owner_id
            """;

    private final JdbcClient jdbc;

    public ProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProjectRef> findBySlug(String slug) {
        return jdbc.sql(SELECT + " where p.slug = :slug").param("slug", slug).query(ProjectRepository::map).optional();
    }

    public Optional<ProjectRef> findById(long id) {
        return jdbc.sql(SELECT + " where p.id = :id").param("id", id).query(ProjectRepository::map).optional();
    }

    public List<ProjectRef> listPublic(int offset, int limit) {
        return jdbc.sql(SELECT + " where p.visibility = 'PUBLIC' order by p.slug offset :offset limit :limit")
                .param("offset", offset)
                .param("limit", limit)
                .query(ProjectRepository::map)
                .list();
    }

    public List<Long> allIds() {
        return jdbc.sql("select id from projects order by id").query(Long.class).list();
    }

    public long create(String slug, String name, String visibility, String defaultBranch, Instant now) {
        return jdbc.sql("""
                insert into projects (slug, name, visibility, default_branch, created_at)
                values (:slug, :name, :visibility, :branch, :now)
                returning id
                """)
                .param("slug", slug)
                .param("name", name)
                .param("visibility", visibility)
                .param("branch", defaultBranch)
                .param("now", Db.ts(now))
                .query(Long.class)
                .single();
    }

    private static ProjectRef map(ResultSet rs, int row) throws SQLException {
        DetectionSettings defaults = DetectionSettings.defaults();
        DetectionSettings settings;
        try {
            settings = new DetectionSettings(
                    orDefault(Db.nullableInt(rs, "window_days"), defaults.windowDays()),
                    orDefault(Db.nullableInt(rs, "suspected_recoveries"), defaults.suspectedRecoveries()),
                    orDefault(Db.nullableInt(rs, "broken_streak"), defaults.brokenStreak()));
        } catch (IllegalArgumentException outOfRange) {
            settings = defaults;
        }
        return new ProjectRef(
                rs.getLong("id"),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("visibility"),
                rs.getString("default_branch"),
                settings,
                rs.getString("owner_login"),
                Db.instant(rs, "created_at"));
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
