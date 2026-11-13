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

@DisplayName("T-0.8: Flyway V1 migration runs on PostgreSQL 17")
class FlywayMigrationIT extends AbstractPostgresIT {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("T-0.8: V1 migration creates users, projects, and api_tokens tables")
    void v1MigrationCreatesExpectedTables() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(null, "public", "%", new String[] {"TABLE"})) {
                Set<String> tables = new HashSet<>();
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
                assertThat(tables).contains("users", "projects", "api_tokens", "flyway_schema_history");
            }
        }
    }
}
