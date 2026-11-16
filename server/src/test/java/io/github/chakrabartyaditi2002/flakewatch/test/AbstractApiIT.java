package io.github.chakrabartyaditi2002.flakewatch.test;

import static io.restassured.RestAssured.given;

import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobWorker;
import io.github.chakrabartyaditi2002.flakewatch.platform.time.MutableClock;
import io.github.chakrabartyaditi2002.flakewatch.projects.TokenScope;
import io.github.chakrabartyaditi2002.flakewatch.projects.TokenService;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Base class for API scenarios: a real server on a random port, a real PostgreSQL, a clock the
 * test controls, and helpers that behave like a CI job uploading results.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AbstractApiIT.TestClock.class)
public abstract class AbstractApiIT extends AbstractPostgresIT {

    /** "Today" in every scenario: Monday 16 November 2026, noon UTC. */
    protected static final Instant TODAY = Instant.parse("2026-11-16T12:00:00Z");

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return MutableClock.of(TODAY);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JobWorker worker;

    @Autowired
    protected TokenService tokens;

    @Autowired
    protected JdbcClient jdbc;

    protected static OpenApiValidationFilter contract;

    @BeforeEach
    void setUpApi() {
        RestAssured.port = port;
        clock.set(TODAY);
        if (contract == null) {
            contract = ContractFilter.create();
        }
    }

    /** Creates a project the way the bootstrap command does, and returns its ingest token. */
    protected String createProject(String slug, String visibility) {
        long id = jdbc.sql("""
                insert into projects (slug, name, visibility, default_branch, created_at)
                values (:slug, :slug, :visibility, 'main', :now) returning id
                """)
                .param("slug", slug)
                .param("visibility", visibility)
                .param("now", clock.instant().atOffset(java.time.ZoneOffset.UTC))
                .query(Long.class)
                .single();
        return tokens.issue(id, "ci", TokenScope.INGEST).value();
    }

    protected long projectId(String slug) {
        return jdbc.sql("select id from projects where slug = :slug").param("slug", slug).query(Long.class).single();
    }

    /** Starts describing one CI job's upload. */
    protected CiJob ciJob(String token) {
        return new CiJob(token);
    }

    /** Runs every queued background job, as the worker would after an upload. */
    protected void processQueuedJobs() {
        worker.runUntilIdle();
    }

    protected RequestSpecification api() {
        return given().filter(contract);
    }

    /** One CI job: its metadata and the report files it uploads. */
    protected final class CiJob {
        private final String token;
        private String commit = "3f2a9c1d";
        private String branch = "main";
        private String runId = "9876543210";
        private int attempt = 1;
        private String jobKey = "test";
        private String variant = "";
        private Instant startedAt;
        private final List<Object[]> files = new ArrayList<>();

        private CiJob(String token) {
            this.token = token;
        }

        public CiJob commit(String sha) {
            this.commit = sha;
            return this;
        }

        public CiJob branch(String name) {
            this.branch = name;
            return this;
        }

        public CiJob run(String id) {
            this.runId = id;
            return this;
        }

        public CiJob attempt(int n) {
            this.attempt = n;
            return this;
        }

        public CiJob job(String key) {
            this.jobKey = key;
            return this;
        }

        public CiJob variant(String v) {
            this.variant = v;
            return this;
        }

        /** When the CI run started, as the GitHub Action would report it. */
        public CiJob startedDaysAgo(double days) {
            this.startedAt = clock.instant().minus(Duration.ofMinutes(Math.round(days * 24 * 60)));
            return this;
        }

        public CiJob report(String fileName, byte[] content) {
            files.add(new Object[] {fileName, content});
            return this;
        }

        public CiJob report(SurefireXml report) {
            return report(report.fileName(), report.bytes());
        }

        public String metadataJson() {
            return "{\"commitSha\":\"" + commit + "\",\"branch\":\"" + branch + "\",\"provider\":\"GITHUB\","
                    + "\"runId\":\"" + runId + "\",\"runAttempt\":" + attempt + ",\"jobKey\":\"" + jobKey + "\","
                    + "\"variant\":\"" + variant + "\""
                    + (startedAt == null ? "" : ",\"runStartedAt\":\"" + startedAt + "\"") + "}";
        }

        public Response upload() {
            RequestSpecification request = given();
            if (token != null) {
                request = request.header("Authorization", "Bearer " + token);
            }
            request = request.multiPart("metadata", metadataJson(), "application/json");
            for (Object[] f : files) {
                request = request.multiPart("files", (String) f[0], (byte[]) f[1], "application/xml");
            }
            return request.post("/api/v1/uploads");
        }
    }
}
