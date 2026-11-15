package io.github.chakrabartyaditi2002.flakewatch.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MigrationScriptValidationTest {

    @Test
    @DisplayName("FR-ING-010: V2 migration script exists and defines ci_runs, uploads, upload_files, and jobs")
    void v2MigrationScriptDefinesRequiredTables() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/db/migration/V2__runs_uploads_jobs.sql");
        assertThat(stream)
                .as("V2__runs_uploads_jobs.sql should exist on classpath")
                .isNotNull();

        String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        assertThat(sql).contains("create table ci_runs");
        assertThat(sql).contains("create table uploads");
        assertThat(sql).contains("create table upload_files");
        assertThat(sql).contains("create table jobs");
        assertThat(sql).contains("references projects(id) on delete cascade");
    }

    @Test
    @DisplayName("FR-ING-010: V3 migration script exists and defines test_cases, failure_groups, test_executions, and test_daily_stats")
    void v3MigrationScriptDefinesRequiredTables() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/db/migration/V3__tests_executions_daily_stats.sql");
        assertThat(stream)
                .as("V3__tests_executions_daily_stats.sql should exist on classpath")
                .isNotNull();

        String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        assertThat(sql).contains("create extension if not exists pg_trgm");
        assertThat(sql).contains("create table test_cases");
        assertThat(sql).contains("create table failure_groups");
        assertThat(sql).contains("create table test_executions");
        assertThat(sql).contains("create table test_daily_stats");
        assertThat(sql).contains("execution_index");
        assertThat(sql).contains("duration_ms");
    }
}
