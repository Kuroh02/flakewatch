package io.github.chakrabartyaditi2002.flakewatch.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.github.chakrabartyaditi2002.flakewatch.jobs.DailyJobs;
import io.github.chakrabartyaditi2002.flakewatch.test.AbstractApiIT;
import io.github.chakrabartyaditi2002.flakewatch.test.SurefireXml;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A team's nightly builds over a couple of weeks, checked through the API the dashboard uses.
 * FR-DET-002 to FR-DET-005, FR-DET-008 to FR-DET-010, Constitution P1.
 */
@DisplayName("Detection over a series of real-looking nightly builds")
class DetectionOverTimeIT extends AbstractApiIT {

    @Autowired
    private DailyJobs dailyJobs;

    /** One nightly build on main: a new commit and run number each night. */
    private void nightly(String token, int nightsAgo, SurefireXml report) {
        ciJob(token)
                .commit(String.format("c0ffee%02d", nightsAgo))
                .run("nightly-" + nightsAgo)
                .startedDaysAgo(nightsAgo)
                .report(report)
                .upload()
                .then()
                .statusCode(202);
        processQueuedJobs();
    }

    private Response test(String slug, String name) {
        Response list = api().queryParam("q", name).get("/api/v1/projects/" + slug + "/tests");
        list.then().statusCode(200);
        long id = list.jsonPath().getLong("items[0].id");
        return api().get("/api/v1/projects/" + slug + "/tests/" + id);
    }

    @Test
    @DisplayName("FR-DET-005: a payment test that breaks and stays red for three nights is Broken; one red night is Failing")
    void brokenAfterThreeRedNights() {
        String token = createProject("shop", "PUBLIC");
        for (int night = 6; night >= 0; night--) {
            SurefireXml report = SurefireXml.suite("com.shop.PaymentTest").passed("refundsCard");
            report = night <= 2 ? report.failed("chargesCard", "expected 200 but was 502") : report.passed("chargesCard");
            report = night == 0 ? report.failed("storesReceipt", "receipt id was null") : report.passed("storesReceipt");
            nightly(token, night, report);
        }

        test("shop", "chargesCard").then().body("status", equalTo("BROKEN"));
        test("shop", "storesReceipt").then().body("status", equalTo("FAILING"));
        test("shop", "refundsCard").then().body("status", equalTo("STABLE"));
    }

    @Test
    @DisplayName("FR-DET-004, P1: a test that fails and recovers on its own twice is only Suspected, never Flaky without proof")
    void recoveriesWithoutProofAreSuspected() {
        String token = createProject("shop", "PUBLIC");
        boolean[] red = {false, true, false, false, true, false, false};
        for (int i = 0; i < red.length; i++) {
            int night = red.length - 1 - i;
            SurefireXml report = SurefireXml.suite("com.shop.SearchTest");
            report = red[i] ? report.failed("findsShoes", "timed out after 5000 ms") : report.passed("findsShoes");
            nightly(token, night, report);
        }

        Response detail = test("shop", "findsShoes");
        detail.then().body("status", equalTo("SUSPECTED")).body("evidence.size()", equalTo(0));
    }

    @Test
    @DisplayName("FR-DET-003: re-running the same commit on a pull request branch with a different result proves flakiness")
    void sameCommitRerunOnPullRequest() {
        String token = createProject("shop", "PUBLIC");
        nightly(token, 3, SurefireXml.suite("com.shop.CartTest").passed("addsItem"));
        nightly(token, 2, SurefireXml.suite("com.shop.CartTest").passed("addsItem"));

        // PR #42: first attempt fails, the developer clicks "Re-run jobs" (attempt 2), it passes
        ciJob(token).branch("feature/cart-badge").commit("abcdef12").run("555").attempt(1).startedDaysAgo(1)
                .report(SurefireXml.suite("com.shop.CartTest").failed("addsItem", "badge count was 0")).upload();
        ciJob(token).branch("feature/cart-badge").commit("abcdef12").run("555").attempt(2).startedDaysAgo(0.9)
                .report(SurefireXml.suite("com.shop.CartTest").passed("addsItem")).upload();
        processQueuedJobs();

        Response detail = test("shop", "addsItem");
        detail.then().body("status", equalTo("FLAKY")).body("evidence[0].kind", equalTo("SAME_COMMIT_MIXED"));
        assertThat(detail.jsonPath().getString("evidence[0].explanation"))
                .isEqualTo("Passed once and failed once on the same commit abcdef1");
    }

    @Test
    @DisplayName("FR-DET-009, FR-DET-010: proof older than the 14-day window stops counting after the daily re-check")
    void oldEvidenceExpires() {
        String token = createProject("shop", "PUBLIC");
        nightly(token, 6, SurefireXml.suite("com.shop.LoginTest").flaky("logsIn", "session cookie missing"));
        for (int night = 5; night >= 0; night--) {
            nightly(token, night, SurefireXml.suite("com.shop.LoginTest").passed("logsIn"));
        }
        test("shop", "logsIn").then().body("status", equalTo("FLAKY"));

        // Ten days later, with no new uploads: the morning's first request queues the daily re-check
        clock.advance(Duration.ofDays(10));
        dailyJobs.ensureToday();
        processQueuedJobs();

        test("shop", "logsIn").then().body("status", equalTo("STABLE"));
        List<String> history = jdbc.sql("""
                select coalesce(from_status, '-') || '>' || to_status from test_status_events
                 order by occurred_at, id
                """).query(String.class).list();
        assertThat(history).containsExactly("->FLAKY", "FLAKY>STABLE");
    }

    @Test
    @DisplayName("FR-DET-001: a test flaky only on firefox is Flaky overall and shows the per-browser breakdown (US-04 #2)")
    void flakyOnlyOnFirefox() {
        String token = createProject("web", "PUBLIC");
        for (int night = 3; night >= 0; night--) {
            String run = "e2e-" + night;
            ciJob(token).run(run).commit(String.format("beef00%02d", night)).startedDaysAgo(night)
                    .job("chrome").variant("chrome")
                    .report(SurefireXml.suite("e2e.CheckoutFlow").passed("paysWithCard")).upload();
            SurefireXml firefox = SurefireXml.suite("e2e.CheckoutFlow");
            firefox = night == 1 ? firefox.flaky("paysWithCard", "element not clickable") : firefox.passed("paysWithCard");
            ciJob(token).run(run).commit(String.format("beef00%02d", night)).startedDaysAgo(night)
                    .job("firefox").variant("firefox").report(firefox).upload();
        }
        processQueuedJobs();

        Response detail = test("web", "paysWithCard");
        detail.then().body("status", equalTo("FLAKY"));
        assertThat(detail.jsonPath().getString("variants.find { it.variant == 'chrome' }.status")).isEqualTo("STABLE");
        assertThat(detail.jsonPath().getString("variants.find { it.variant == 'firefox' }.status")).isEqualTo("FLAKY");
    }

    @Test
    @DisplayName("FR-DET-012, US-03 #1: the flaky list is ranked by impact, worst first")
    void flakyListRankedByImpact() {
        String token = createProject("shop", "PUBLIC");
        for (int night = 4; night >= 0; night--) {
            SurefireXml report = SurefireXml.suite("com.shop.OrderTest").flaky("placesOrder", "deadlock detected");
            report = night % 2 == 0 ? report.flaky("cancelsOrder", "stale order version") : report.passed("cancelsOrder");
            nightly(token, night, report.passed("listsOrders"));
        }

        Response flaky = api().queryParam("status", "FLAKY").get("/api/v1/projects/shop/tests");
        flaky.then().statusCode(200).body("items.size()", equalTo(2));
        assertThat(flaky.jsonPath().getList("items.name", String.class)).containsExactly("placesOrder", "cancelsOrder");
        assertThat(flaky.jsonPath().getInt("items[0].impact")).isGreaterThan(flaky.jsonPath().getInt("items[1].impact"));
    }
}
