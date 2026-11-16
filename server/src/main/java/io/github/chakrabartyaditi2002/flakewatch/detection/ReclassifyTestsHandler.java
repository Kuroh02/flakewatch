package io.github.chakrabartyaditi2002.flakewatch.detection;

import io.github.chakrabartyaditi2002.flakewatch.jobs.Job;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobHandler;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobKinds;
import io.github.chakrabartyaditi2002.flakewatch.platform.json.Json;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Recomputes statuses for the tests in one upload (FR-DET-008). */
@Component
public class ReclassifyTestsHandler implements JobHandler {

    private final DetectionService detection;
    private final Clock clock;

    public ReclassifyTestsHandler(DetectionService detection, Clock clock) {
        this.detection = detection;
        this.clock = clock;
    }

    @Override
    public String kind() {
        return JobKinds.RECLASSIFY_TESTS;
    }

    @Override
    public void handle(Job job) {
        long projectId = Json.asLong(job.payload().get("projectId"));
        List<Long> testIds = ((List<?>) job.payload().get("testIds")).stream().map(Json::asLong).toList();
        Instant now = clock.instant();
        detection.reclassify(projectId, testIds, now);
        detection.snapshot(projectId, now);
    }
}
