package io.github.chakrabartyaditi2002.flakewatch.scenario;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.chakrabartyaditi2002.flakewatch.core.report.Fixtures;
import io.github.chakrabartyaditi2002.flakewatch.projects.TokenScope;
import io.github.chakrabartyaditi2002.flakewatch.test.AbstractApiIT;
import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NFR-SEC-003, PLAN §12.3: every operation in api/openapi.yaml, called by every kind of caller.
 *
 * <p>The list of operations is read from the contract, so adding an endpoint there without adding it
 * here fails this test.
 */
@DisplayName("NFR-SEC-003: authorization matrix")
class AuthorizationMatrixIT extends AbstractApiIT {

    /** Callers that exist in M1. Browser sessions arrive with GitHub sign-in in M2. */
    enum Caller { ANONYMOUS, INGEST_TOKEN, READ_TOKEN, OTHER_PROJECT_INGEST_TOKEN }

    /** Read operations: path template → expected status for {public project, private project}, per caller. */
    private static final Map<String, String> PUBLIC_READS = new LinkedHashMap<>();

    static {
        PUBLIC_READS.put("getProject", "/api/v1/projects/{slug}");
        PUBLIC_READS.put("getProjectSummary", "/api/v1/projects/{slug}/summary");
        PUBLIC_READS.put("listTests", "/api/v1/projects/{slug}/tests");
        PUBLIC_READS.put("getTest", "/api/v1/projects/{slug}/tests/{testId}");
        PUBLIC_READS.put("listExecutions", "/api/v1/projects/{slug}/tests/{testId}/executions");
        PUBLIC_READS.put("listRuns", "/api/v1/projects/{slug}/runs");
        PUBLIC_READS.put("getRun", "/api/v1/projects/{slug}/runs/{runId}");
        PUBLIC_READS.put("listFailureGroups", "/api/v1/projects/{slug}/failure-groups");
        PUBLIC_READS.put("getFailureGroup", "/api/v1/projects/{slug}/failure-groups/{groupId}");
    }

    /** Operations checked by their own tests below. */
    private static final Set<String> CHECKED_SEPARATELY =
            Set.of("createUpload", "getUpload", "listProjects", "getVersion");

    /**
     * In the contract but deliberately not built in M1 (sign-in, tokens API, settings, quarantine: M2).
     * They must not answer as if they worked.
     */
    private static final Map<String, String[]> NOT_BUILT_YET = new LinkedHashMap<>();

    static {
        NOT_BUILT_YET.put("getMe", new String[] {"GET", "/api/v1/me"});
        NOT_BUILT_YET.put("createProject", new String[] {"POST", "/api/v1/projects"});
        NOT_BUILT_YET.put("updateProject", new String[] {"PATCH", "/api/v1/projects/{slug}"});
        NOT_BUILT_YET.put("deleteProject", new String[] {"DELETE", "/api/v1/projects/{slug}"});
        NOT_BUILT_YET.put("listTokens", new String[] {"GET", "/api/v1/projects/{slug}/tokens"});
        NOT_BUILT_YET.put("createToken", new String[] {"POST", "/api/v1/projects/{slug}/tokens"});
        NOT_BUILT_YET.put("revokeToken", new String[] {"DELETE", "/api/v1/projects/{slug}/tokens/1"});
        NOT_BUILT_YET.put("getQuarantineList", new String[] {"GET", "/api/v1/projects/{slug}/quarantine"});
        NOT_BUILT_YET.put("createQuarantine", new String[] {"POST", "/api/v1/projects/{slug}/quarantine"});
        NOT_BUILT_YET.put("releaseQuarantine", new String[] {"DELETE", "/api/v1/projects/{slug}/quarantine/1"});
    }

    private final Map<Caller, String> tokenOf = new LinkedHashMap<>();
    private final Map<String, Long> ids = new LinkedHashMap<>();

    @BeforeEach
    void twoProjects() {
        String demoToken = createProject("demo", "PUBLIC");
        String secretToken = createProject("secret-app", "PRIVATE");
        createProject("other", "PUBLIC");
        tokenOf.put(Caller.INGEST_TOKEN, demoToken);
        tokenOf.put(Caller.READ_TOKEN, tokens.issue(projectId("demo"), "read", TokenScope.READ).value());
        tokenOf.put(Caller.OTHER_PROJECT_INGEST_TOKEN, tokens.issue(projectId("other"), "ci", TokenScope.INGEST).value());

        byte[] report = Fixtures.bytes("maven-surefire/TEST-sample.SampleTest.xml");
        ciJob(demoToken).run("1").report("TEST-sample.SampleTest.xml", report).upload().then().statusCode(202);
        ciJob(secretToken).run("1").report("TEST-sample.SampleTest.xml", report).upload().then().statusCode(202);
        processQueuedJobs();
        for (String slug : List.of("demo", "secret-app")) {
            long project = projectId(slug);
            ids.put(slug + ".test", first("select min(id) from test_cases where project_id = " + project));
            ids.put(slug + ".run", first("select min(id) from ci_runs where project_id = " + project));
            ids.put(slug + ".group", first("select min(id) from failure_groups where project_id = " + project));
        }
    }

    private long first(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private String path(String template, String slug) {
        return template.replace("{slug}", slug)
                .replace("{testId}", String.valueOf(ids.get(slug + ".test")))
                .replace("{runId}", String.valueOf(ids.get(slug + ".run")))
                .replace("{groupId}", String.valueOf(ids.get(slug + ".group")));
    }

    private RequestSpecification as(Caller caller) {
        RequestSpecification request = given();
        String token = tokenOf.get(caller);
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    @Test
    @DisplayName("NFR-SEC-003: every operation in the contract has an entry in this matrix")
    void everyOperationIsCovered() throws IOException {
        Set<String> covered = new TreeSet<>(PUBLIC_READS.keySet());
        covered.addAll(CHECKED_SEPARATELY);
        covered.addAll(NOT_BUILT_YET.keySet());
        assertThat(new TreeSet<>(operationIdsInContract())).isEqualTo(covered);
    }

    @Test
    @DisplayName("FR-PRJ-004: public project data is readable by anyone; a private project is 404 for every caller")
    void readsOfPublicAndPrivateProjects() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> op : PUBLIC_READS.entrySet()) {
            for (Caller caller : Caller.values()) {
                int onPublic = as(caller).get(path(op.getValue(), "demo")).statusCode();
                int onPrivate = as(caller).get(path(op.getValue(), "secret-app")).statusCode();
                if (onPublic != 200) {
                    problems.add(op.getKey() + " as " + caller + " on a public project: " + onPublic);
                }
                if (onPrivate != 404) {
                    problems.add(op.getKey() + " as " + caller + " on a private project: " + onPrivate);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("FR-PRJ-005: uploads need an ingest token; status is visible only to the uploading project")
    void uploadOperations() {
        byte[] report = Fixtures.bytes("maven-surefire/TEST-sample.SampleTest.xml");
        Map<Caller, Integer> expectedUpload = Map.of(
                Caller.ANONYMOUS, 401, Caller.READ_TOKEN, 403, Caller.INGEST_TOKEN, 202,
                Caller.OTHER_PROJECT_INGEST_TOKEN, 202);
        for (Caller caller : Caller.values()) {
            int status = ciJob(tokenOf.get(caller)).run("matrix-" + caller).report("TEST-a.xml", report).upload().statusCode();
            assertThat(status).as("createUpload as " + caller).isEqualTo(expectedUpload.get(caller));
        }

        long demoUpload = first("select min(id) from uploads where project_id = " + projectId("demo"));
        Map<Caller, Integer> expectedStatus = Map.of(
                Caller.ANONYMOUS, 401, Caller.READ_TOKEN, 403, Caller.INGEST_TOKEN, 200,
                Caller.OTHER_PROJECT_INGEST_TOKEN, 404);
        for (Caller caller : Caller.values()) {
            int status = as(caller).get("/api/v1/uploads/" + demoUpload).statusCode();
            assertThat(status).as("getUpload as " + caller).isEqualTo(expectedStatus.get(caller));
        }
    }

    @Test
    @DisplayName("FR-PRJ-004: the project list and version are public, and the list never shows private projects")
    void publicMetaOperations() {
        for (Caller caller : Caller.values()) {
            assertThat(as(caller).get("/api/v1/version").statusCode()).isEqualTo(200);
            List<String> slugs = as(caller).get("/api/v1/projects").jsonPath().getList("items.slug", String.class);
            assertThat(slugs).doesNotContain("secret-app");
        }
    }

    @Test
    @DisplayName("NFR-SEC-003: operations planned for M2 don't exist yet, for any caller")
    void notBuiltYet() {
        for (Map.Entry<String, String[]> op : NOT_BUILT_YET.entrySet()) {
            for (Caller caller : Caller.values()) {
                String method = op.getValue()[0];
                String url = path(op.getValue()[1], "demo");
                int status = as(caller).contentType("application/json").body("{}").request(method, url).statusCode();
                assertThat(status).as(op.getKey() + " as " + caller).isIn(404, 405);
            }
        }
    }

    private static List<String> operationIdsInContract() throws IOException {
        Path spec = Path.of("..", "api", "openapi.yaml");
        if (!Files.exists(spec)) {
            spec = Path.of("api", "openapi.yaml");
        }
        List<String> operationIds = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^\\s+operationId:\\s*(\\w+)\\s*$").matcher(Files.readString(spec));
        while (m.find()) {
            operationIds.add(m.group(1));
        }
        return operationIds;
    }
}
