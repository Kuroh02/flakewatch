import { Link } from '../components/Link';
import { StatusBadge } from '../components/StatusBadge';
import { Empty, ErrorState, Loading } from '../components/States';
import { TrendChart } from '../components/TrendChart';
import type { ProjectSummary } from '../lib/api';
import { plural } from '../lib/format';
import { STATUS_ORDER } from '../lib/status';
import { useApi } from '../lib/useApi';
import { href } from '../lib/url';
import { RunsTable } from './RunsList';

/** Project overview: tests per status, the 30-day flaky trend and recent runs (FR-UI-001). */
export function Overview({ slug }: { slug: string }) {
  const summary = useApi<ProjectSummary>(`/api/v1/projects/${slug}/summary`);
  if (summary.status === 'loading') {
    return <Loading what="the overview" />;
  }
  if (summary.status === 'error') {
    return <ErrorState error={summary.error} onRetry={summary.reload} />;
  }
  const s = summary.data;
  if (s.totalTests === 0) {
    return (
      <Empty title="No results yet.">
        Add the Flakewatch step to your CI, run your tests, and the results will appear here within a minute.
      </Empty>
    );
  }

  return (
    <>
      <h1>Overview</h1>
      <p className="muted">{plural(s.totalTests, 'test')} tracked.</p>
      <ul className="status-tiles">
        {STATUS_ORDER.map((status) => (
          <li key={status}>
            <Link href={href({ name: 'tests', slug }, { status })} className="tile">
              <span className="tile-count">{s.countsByStatus[status]}</span>
              <StatusBadge status={status} />
            </Link>
          </li>
        ))}
      </ul>

      <h2>Flaky tests over the last 30 days</h2>
      {s.flakyTrend.length === 0 ? <p className="muted">The trend starts after the first full day of results.</p> : <TrendChart points={s.flakyTrend} />}

      <h2>Recent runs</h2>
      <RunsTable slug={slug} runs={s.recentRuns} />
    </>
  );
}
