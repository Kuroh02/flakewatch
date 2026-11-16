package io.github.chakrabartyaditi2002.flakewatch.scenario;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import io.github.chakrabartyaditi2002.flakewatch.core.report.Fixtures;
import io.github.chakrabartyaditi2002.flakewatch.test.AbstractApiIT;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-09: a visitor browses the public demo without signing in. Every response is checked against
 * api/openapi.yaml (NFR-QUAL-003), and private projects stay invisible (FR-PRJ-004).
 */
@DisplayName("US-09: browsing the public dashboard API")
class PublicDashboardApiIT extends AbstractApiIT {

    private long runId;

    @BeforeEach
    void demoProjectWithData() {
        String token = createProject("demo", "PUBLIC");
        runId = ciJob(token).report("TEST-sample.SampleTest.xml", Fixtures.bytes("maven-surefire/TEST-sample.SampleTest.xml"))
                .upload().jsonPath().getLong("runId");
        String secret = createProject("secret-app", "PRIVATE");
        ciJob(secret).run("1").report("results.xml", Fixtures.bytes("playwright/results.xml")).upload();
        processQueuedJobs();
    }

    @Test
    @DisplayName("FR-UI-001: the project list shows public projects only, and the overview has counts and recent runs")
    void projectListAndOverview() {
        api().get("/api/v1/projects").then().statusCode(200)
                .body("items.size()", equalTo(1))
                .body("items[0].slug", equalTo("demo"))
                .body("items[0].settings.windowDays", equalTo(14));

        api().get("/api/v1/projects/demo/summary").then().statusCode(200)
                .body("totalTests", equalTo(7))
                .body("countsByStatus.FLAKY", equalTo(1))
                .body("recentRuns[0].id", equalTo((int) runId))
                .body("flakyTrend[0].flaky", equalTo(1));
    }

    @Test
    @DisplayName("FR-UI-002, FR-UI-003, FR-UI-004, FR-UI-005: every read endpoint answers in the documented shape")
    void everyReadEndpointMatchesTheContract() {
        Response tests = api().queryParam("sort", "name").queryParam("limit", 3).get("/api/v1/projects/demo/tests");
        tests.then().statusCode(200).body("items.size()", equalTo(3)).body("nextCursor", notNullValue());
        api().queryParam("sort", "name").queryParam("limit", 3).queryParam("cursor", tests.jsonPath().getString("nextCursor"))
                .get("/api/v1/projects/demo/tests").then().statusCode(200).body("items.size()", equalTo(3));

        long testId = tests.jsonPath().getLong("items[0].id");
        api().get("/api/v1/projects/demo/tests/" + testId).then().statusCode(200);
        api().get("/api/v1/projects/demo/tests/" + testId + "/executions").then().statusCode(200);
        api().get("/api/v1/projects/demo/runs").then().statusCode(200).body("items[0].id", equalTo((int) runId));
        api().get("/api/v1/projects/demo/runs/" + runId).then().statusCode(200)
                .body("failures.label", hasItem("PASSED_ON_RETRY"));

        Response groups = api().get("/api/v1/projects/demo/failure-groups");
        groups.then().statusCode(200);
        api().get("/api/v1/projects/demo/failure-groups/" + groups.jsonPath().getLong("items[0].id"))
                .then().statusCode(200).body("tests.size()", equalTo(groups.jsonPath().getInt("items[0].affectedTests")));
    }

    @Test
    @DisplayName("FR-PRJ-004, US-09 #2: a private project answers 404 everywhere, exactly like a project that doesn't exist")
    void privateProjectIsInvisible() {
        for (String path : new String[] {"", "/summary", "/tests", "/runs", "/failure-groups"}) {
            api().get("/api/v1/projects/secret-app" + path).then().statusCode(404).body("code", equalTo("not-found"));
            api().get("/api/v1/projects/no-such-app" + path).then().statusCode(404).body("code", equalTo("not-found"));
        }
    }

    @Test
    @DisplayName("FR-PRJ-004: IDs from another project can't be read through the demo project's URLs")
    void idsAreScopedToTheirProject() {
        long privateTest = jdbc.sql("""
                select tc.id from test_cases tc join projects p on p.id = tc.project_id
                 where p.slug = 'secret-app' limit 1
                """).query(Long.class).single();
        api().get("/api/v1/projects/demo/tests/" + privateTest).then().statusCode(404);
        api().get("/api/v1/projects/demo/tests/" + privateTest + "/executions").then().statusCode(404);
    }

    @Test
    @DisplayName("FR-UI-002: bad filters are a 400, not a crash")
    void badFiltersAreRejected() {
        // Sent without the contract filter: these requests are deliberately outside the contract
        given().queryParam("cursor", "not-a-cursor").get("/api/v1/projects/demo/tests").then().statusCode(400)
                .body("code", equalTo("invalid-cursor"));
        given().queryParam("limit", 500).get("/api/v1/projects/demo/runs").then().statusCode(400)
                .body("code", equalTo("invalid-limit"));
    }
}
