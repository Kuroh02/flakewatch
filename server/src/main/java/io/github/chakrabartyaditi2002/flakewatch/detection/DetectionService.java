package io.github.chakrabartyaditi2002.flakewatch.detection;

import io.github.chakrabartyaditi2002.flakewatch.core.detection.Classification;
import io.github.chakrabartyaditi2002.flakewatch.core.detection.Classifier;
import io.github.chakrabartyaditi2002.flakewatch.core.detection.DetectionSettings;
import io.github.chakrabartyaditi2002.flakewatch.core.detection.Execution;
import io.github.chakrabartyaditi2002.flakewatch.detection.internal.StatusRepository;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobKinds;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobQueue;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectAccess;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectRef;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Recomputes test statuses with the pure {@link Classifier} and stores them with their history
 * (FR-DET-008, FR-DET-009, detection rules §11).
 */
@Service
public class DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);
    public static final int BATCH = 500;

    private final StatusRepository statuses;
    private final ProjectAccess projects;
    private final JobQueue jobs;

    public DetectionService(StatusRepository statuses, ProjectAccess projects, JobQueue jobs) {
        this.statuses = statuses;
        this.projects = projects;
        this.jobs = jobs;
    }

    /**
     * After an upload: queue a re-check only for tests whose status could change (detection rules §11).
     * Tests that are Stable with no failures in the window are skipped safely, by PROP-DET-08.
     */
    public void queueReclassification(long projectId, Set<Long> uploadTestIds, Set<Long> failedInUpload, Instant now) {
        if (uploadTestIds.isEmpty()) {
            return;
        }
        ProjectRef project = projects.byId(projectId);
        Instant windowStart = now.minus(Duration.ofDays(project.settings().windowDays()));
        Set<Long> selected = new LinkedHashSet<>(failedInUpload);
        selected.addAll(statuses.needingRecheck(uploadTestIds, windowStart));
        List<Long> ids = new ArrayList<>(selected);
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<Long> chunk = ids.subList(from, Math.min(ids.size(), from + BATCH));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("projectId", projectId);
            payload.put("testIds", new ArrayList<>(chunk));
            jobs.enqueue(JobKinds.RECLASSIFY_TESTS, payload, null, now);
        }
        log.info("detection.queued projectId={} tests={} skipped={}", projectId, ids.size(), uploadTestIds.size() - ids.size());
    }

    /** Classifies the given tests and saves any changes. Runs inside the caller's transaction. */
    public int reclassify(long projectId, Collection<Long> testIds, Instant now) {
        if (testIds.isEmpty()) {
            return 0;
        }
        ProjectRef project = projects.byId(projectId);
        DetectionSettings settings = project.settings();
        Instant windowStart = now.minus(Duration.ofDays(settings.windowDays()));
        Map<Long, List<Execution>> executions = statuses.executionsInWindow(testIds, windowStart);
        Map<Long, String> current = statuses.currentStatuses(testIds);
        int changes = 0;
        for (Long testId : testIds) {
            Classification c = Classifier.classify(
                    executions.getOrDefault(testId, List.of()), settings, now, project.defaultBranch());
            String before = current.get(testId);
            statuses.save(projectId, testId, c, now);
            if (before == null || !before.equals(c.overall().name())) {
                statuses.recordChange(projectId, testId, before, c.overall().name(), c.reason(), now);
                changes++;
                log.info("status.changed testId={} from={} to={}", testId, before, c.overall());
            }
        }
        return changes;
    }

    /** Recomputes every test in a project in batches, then records today's counts (FR-DET-010, FR-UI-001). */
    public void reclassifyProject(long projectId, Instant now) {
        List<Long> all = statuses.testIds(projectId);
        for (int from = 0; from < all.size(); from += BATCH) {
            reclassify(projectId, all.subList(from, Math.min(all.size(), from + BATCH)), now);
        }
        snapshot(projectId, now);
    }

    public void snapshot(long projectId, Instant now) {
        statuses.snapshot(projectId, LocalDate.ofInstant(now, ZoneOffset.UTC));
    }
}
