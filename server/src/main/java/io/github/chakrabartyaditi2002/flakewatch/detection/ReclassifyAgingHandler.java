package io.github.chakrabartyaditi2002.flakewatch.detection;

import io.github.chakrabartyaditi2002.flakewatch.detection.internal.StatusRepository;
import io.github.chakrabartyaditi2002.flakewatch.jobs.Job;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobHandler;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobKinds;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Daily re-check of every test, so evidence older than the window stops counting (FR-DET-010),
 * plus the daily counts behind the overview's 30-day trend (FR-UI-001).
 */
@Component
public class ReclassifyAgingHandler implements JobHandler {

    private final DetectionService detection;
    private final StatusRepository statuses;
    private final Clock clock;

    public ReclassifyAgingHandler(DetectionService detection, StatusRepository statuses, Clock clock) {
        this.detection = detection;
        this.statuses = statuses;
        this.clock = clock;
    }

    @Override
    public String kind() {
        return JobKinds.RECLASSIFY_AGING;
    }

    @Override
    public void handle(Job job) {
        Instant now = clock.instant();
        for (long projectId : statuses.projectIds()) {
            detection.reclassifyProject(projectId, now);
        }
    }
}
