package io.github.chakrabartyaditi2002.flakewatch.detection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.chakrabartyaditi2002.flakewatch.core.detection.DetectionSettings;
import io.github.chakrabartyaditi2002.flakewatch.detection.internal.StatusRepository;
import io.github.chakrabartyaditi2002.flakewatch.jobs.JobQueue;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectAccess;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Two re-check jobs can cover the same tests: two jobs of one CI run finish at the same moment, each
 * queueing its own tests. Each job saves its statuses in one transaction, so it holds row locks
 * until it commits. If one job saves tests 7 then 3 while another saves 3 then 7, each waits for
 * the other and PostgreSQL aborts one with "deadlock detected" (seen in the e2e suite's CI log).
 * Saving in ascending test id order means they queue behind each other instead.
 */
class DetectionLockOrderTest {

    private static final long PROJECT = 1;
    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    @Test
    @DisplayName("FR-DET-008: statuses are saved in ascending test id order, whatever order the job lists them in")
    void savesInAscendingIdOrder() {
        StatusRepository statuses = mock(StatusRepository.class);
        ProjectAccess projects = mock(ProjectAccess.class);
        when(projects.byId(PROJECT)).thenReturn(new ProjectRef(
                PROJECT, "shop", "Shop", "PUBLIC", "main", DetectionSettings.defaults(), null, NOW));
        when(statuses.executionsInWindow(any(), any())).thenReturn(Map.of());
        when(statuses.currentStatuses(any())).thenReturn(Map.of());
        DetectionService detection = new DetectionService(statuses, projects, mock(JobQueue.class));

        detection.reclassify(PROJECT, List.of(42L, 7L, 19L, 3L), NOW);

        InOrder order = inOrder(statuses);
        for (long id : List.of(3L, 7L, 19L, 42L)) {
            order.verify(statuses).save(eq(PROJECT), eq(id), any(), eq(NOW));
        }
    }
}
