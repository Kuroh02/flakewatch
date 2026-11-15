package io.github.chakrabartyaditi2002.flakewatch.db;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chakrabartyaditi2002.flakewatch.test.AbstractPostgresIT;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("T-1.1: Flyway V1, V2, and V3 migrations run on PostgreSQL 17")
class FlywayMigrationIT extends AbstractPostgresIT {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("FR-ING-010: V1, V2, and V3 migrations create all expected tables")
    void migrationsCreateExpectedTables() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(null, "public", "%", new String[] {"TABLE"})) {
                Set<String> tables = new HashSet<>();
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
                assertThat(tables)
                        .contains(
                                "users",
                                "projects",
                                "api_tokens",
                                "ci_runs",
                                "uploads",
                                "upload_files",
                                "jobs",
                                "test_cases",
                                "failure_groups",
                                "test_executions",
                                "test_daily_stats",
                                "flyway_schema_history");
            }
        }
    }
}
