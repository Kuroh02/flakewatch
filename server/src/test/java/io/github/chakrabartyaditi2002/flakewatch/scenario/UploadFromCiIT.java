package io.github.chakrabartyaditi2002.flakewatch.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;

import io.github.chakrabartyaditi2002.flakewatch.core.report.Fixtures;
import io.github.chakrabartyaditi2002.flakewatch.projects.TokenScope;
import io.github.chakrabartyaditi2002.flakewatch.test.AbstractApiIT;
import io.restassured.response.Response;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** US-01 and US-02: a CI job uploads its JUnit XML, and the run page says which failures are real. */
@DisplayName("US-01/US-02: uploading results from CI")
class UploadFromCiIT extends AbstractApiIT {

    private static final String SUREFIRE = "maven-surefire/TEST-sample.SampleTest.xml";

    @Test
    @DisplayName("FR-ING-003, FR-ING-005: a Maven build's report is accepted, processed in the background, and summarised")
    void mavenBuildIsAcceptedAndProcessed() {
        String token = createProject("shop", "PUBLIC");

        Response accepted = ciJob(token).report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload();
        accepted.then().statusCode(202)
                .body("status", equalTo("RECEIVED"))
                .body("duplicate", equalTo(false));
        long uploadId = accepted.jsonPath().getLong("uploadId");
        long runId = accepted.jsonPath().getLong("runId");
        assertThat(accepted.jsonPath().getString("runUrl")).isEqualTo("https://flakewatch.test/p/shop/runs/" + runId);

        processQueuedJobs();

        // The sample has: 3 passed (one only after a Surefire re-run), 2 failed, 1 errored, 1 skipped
        statusOf(token, uploadId).then().statusCode(200)
                .body("status", equalTo("PROCESSED"))
                .body("summary.tests", equalTo(7))
                .body("summary.passed", equalTo(3))
                .body("summary.failed", equalTo(2))
                .body("summary.errored", equalTo(1))
                .body("summary.skipped", equalTo(1))
                .body("summary.retried", equalTo(1));
    }

    @Test
    @DisplayName("FR-DET-002, FR-UI-004: a test that passed only on Surefire's re-run is labelled and becomes Flaky")
    void surefireRerunMakesATestFlaky() {
        String token = createProject("shop", "PUBLIC");
        long runId = ciJob(token).report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload()
                .jsonPath().getLong("runId");
        processQueuedJobs();

        Response run = api().get("/api/v1/projects/shop/runs/" + runId);
        run.then().statusCode(200);
        assertThat(run.jsonPath().getString("failures.find { it.testKey == 'sample.SampleTest#testFlaky' }.label"))
                .isEqualTo("PASSED_ON_RETRY");
        assertThat(run.jsonPath().getString("failures.find { it.testKey == 'sample.SampleTest#testFailing' }.label"))
                .isEqualTo("NEW_FAILURE");

        Response flaky = api().queryParam("q", "testFlaky").get("/api/v1/projects/shop/tests");
        flaky.then().statusCode(200).body("items[0].status", equalTo("FLAKY"));
        long testId = flaky.jsonPath().getLong("items[0].id");

        api().get("/api/v1/projects/shop/tests/" + testId).then().statusCode(200)
                .body("evidence[0].kind", equalTo("RETRY_PASS"))
                .body("evidence[0].runIds", hasItem((int) runId))
                .body("timeline[0].outcome", equalTo("PASSED_ON_RETRY"));
    }

    @Test
    @DisplayName("FR-ING-004: CI retrying the upload step after a timeout gets the original upload back, with nothing duplicated")
    void retriedUploadStepIsHarmless() {
        String token = createProject("shop", "PUBLIC");
        Response first = ciJob(token).report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload();
        first.then().statusCode(202);
        processQueuedJobs();

        Response retry = ciJob(token).report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload();
        retry.then().statusCode(200)
                .body("duplicate", equalTo(true))
                .body("uploadId", equalTo(first.jsonPath().getInt("uploadId")))
                .body("status", equalTo("PROCESSED"));
        processQueuedJobs();

        assertThat(count("select count(*) from uploads")).isEqualTo(1L);
        assertThat(count("select count(*) from test_executions")).isEqualTo(11L);
    }

    @Test
    @DisplayName("FR-ING-004: the same CI job sending different results is a conflict")
    void sameJobDifferentContentIsAConflict() {
        String token = createProject("shop", "PUBLIC");
        ciJob(token).report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload().then().statusCode(202);

        ciJob(token).report("TEST-sample.SampleGradleTest.xml", Fixtures.bytes("gradle/TEST-sample.SampleGradleTest.xml"))
                .upload().then().statusCode(409)
                .body("code", equalTo("duplicate-upload-different-content"));
    }

    @Test
    @DisplayName("FR-ING-004: a re-run of the whole workflow (attempt 2) is a new upload, not a duplicate")
    void workflowRerunIsANewUpload() {
        String token = createProject("shop", "PUBLIC");
        Response first = ciJob(token).attempt(1).report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload();
        Response second = ciJob(token).attempt(2).report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload();
        second.then().statusCode(202);
        assertThat(second.jsonPath().getLong("runId")).isNotEqualTo(first.jsonPath().getLong("runId"));
    }

    @Test
    @DisplayName("FR-ING-011: chrome and firefox shards of one workflow run are grouped under one run")
    void matrixShardsShareARun() {
        String token = createProject("web", "PUBLIC");
        Response chrome = ciJob(token).job("e2e-chrome").variant("chrome")
                .report("results.xml", Fixtures.bytes("playwright/results.xml")).upload();
        Response firefox = ciJob(token).job("e2e-firefox").variant("firefox")
                .report("results.xml", Fixtures.bytes("playwright/results.xml")).upload();
        chrome.then().statusCode(202);
        firefox.then().statusCode(202);
        assertThat(firefox.jsonPath().getLong("runId")).isEqualTo(chrome.jsonPath().getLong("runId"));
        processQueuedJobs();

        api().get("/api/v1/projects/web/runs/" + chrome.jsonPath().getLong("runId")).then().statusCode(200)
                .body("uploads.size()", equalTo(2))
                .body("totals.tests", equalTo(12));
    }

    @Test
    @DisplayName("FR-ING-011: a shard claiming a different commit for the same run is rejected")
    void shardWithDifferentCommitIsRejected() {
        String token = createProject("shop", "PUBLIC");
        ciJob(token).job("unit").commit("3f2a9c1d").report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE))
                .upload().then().statusCode(202);
        ciJob(token).job("integration").commit("aaaabbbb").report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE))
                .upload().then().statusCode(409)
                .body("code", equalTo("run-metadata-mismatch"));
    }

    @Test
    @DisplayName("FR-ING-001: gzip-compressed reports are accepted and hash the same as uncompressed ones")
    void gzipReportsAreAccepted() throws Exception {
        String token = createProject("shop", "PUBLIC");
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write(Fixtures.bytes(SUREFIRE));
        }
        ciJob(token).report("TEST-sample.SampleTest.xml.gz", gz.toByteArray()).upload().then().statusCode(202);
        // The CI step retries with the uncompressed file: same content, so it's a duplicate, not a conflict
        ciJob(token).report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload().then().statusCode(200);
    }

    @Test
    @DisplayName("FR-ING-008: TestNG's native results file is refused straight away with a clear reason")
    void nonJunitFileIsRejected() {
        String token = createProject("shop", "PUBLIC");
        ciJob(token).report("testng-results.xml", Fixtures.bytes("testng/testng-results.xml")).upload()
                .then().statusCode(400)
                .body("code", equalTo("not-a-junit-report"));
        assertThat(count("select count(*) from uploads")).isEqualTo(0L);
    }

    @Test
    @DisplayName("FR-ING-008: invalid metadata lists every problem at once")
    void invalidMetadataListsEveryField() {
        String token = createProject("shop", "PUBLIC");
        ciJob(token).commit("NOT-A-SHA").run("").report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload()
                .then().statusCode(400)
                .body("code", equalTo("validation-error"))
                .body("errors.field", hasItem("metadata.commitSha"))
                .body("errors.field", hasItem("metadata.runId"));
    }

    @Test
    @DisplayName("FR-PRJ-005, US-06 #4: no token is 401, a read token is 403, a token for another project can't see its uploads")
    void tokensAreScopedToOneProject() {
        String shopToken = createProject("shop", "PUBLIC");
        createProject("blog", "PUBLIC");
        String readToken = tokens.issue(projectId("shop"), "quarantine", TokenScope.READ).value();

        ciJob(null).report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload().then().statusCode(401);
        ciJob(readToken).report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload().then().statusCode(403);

        long uploadId = ciJob(shopToken).report("TEST-sample.SampleTest.xml", Fixtures.bytes(SUREFIRE)).upload()
                .jsonPath().getLong("uploadId");
        String blogToken = tokens.issue(projectId("blog"), "ci", TokenScope.INGEST).value();
        statusOf(blogToken, uploadId).then().statusCode(404);
    }

    @Test
    @DisplayName("FR-ING-008: a report that turns out to be broken XML fails the upload with the file and line")
    void brokenXmlFailsWithAReason() {
        String token = createProject("shop", "PUBLIC");
        // Starts like a report (so it's accepted), but is cut off half-way, as when a CI disk fills up
        byte[] truncated = "<testsuite name=\"a\"><testcase classname=\"a.B\" name=\"t\">".getBytes();
        long uploadId = ciJob(token).report("TEST-a.B.xml", truncated).upload().jsonPath().getLong("uploadId");
        processQueuedJobs();

        statusOf(token, uploadId).then().statusCode(200)
                .body("status", equalTo("FAILED"))
                .body("error", startsWith("file TEST-a.B.xml"));
    }

    private Response statusOf(String token, long uploadId) {
        return api().header("Authorization", "Bearer " + token).get("/api/v1/uploads/" + uploadId);
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
