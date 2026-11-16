import { Link } from '../components/Link';
import { Pager } from '../components/Pager';
import { Empty, ErrorState, Loading } from '../components/States';
import { apiPath, type RunPage, type RunSummary } from '../lib/api';
import { shortSha, timeAgo } from '../lib/format';
import { useApi } from '../lib/useApi';
import { href, updateQuery, type Query } from '../lib/url';

/** CI runs, newest first (FR-UI-004). */
export function RunsList({ slug, query }: { slug: string; query: Query }) {
  const page = useApi<RunPage>(apiPath(`/api/v1/projects/${slug}/runs`, [['branch', query.branch], ['cursor', query.cursor]]));
  return (
    <>
      <h1>Runs</h1>
      {page.status === 'loading' && <Loading what="runs" />}
      {page.status === 'error' && <ErrorState error={page.error} onRetry={page.reload} />}
      {page.status === 'ready' && (
        <>
          <RunsTable slug={slug} runs={page.data.items} />
          <Pager
            nextHref={page.data.nextCursor ? href({ name: 'runs', slug }, updateQuery(query, { cursor: page.data.nextCursor })) : null}
            firstHref={query.cursor ? href({ name: 'runs', slug }, updateQuery(query, { cursor: undefined })) : null}
          />
        </>
      )}
    </>
  );
}

export function RunsTable({ slug, runs }: { slug: string; runs: RunSummary[] }) {
  if (runs.length === 0) {
    return <Empty title="No runs yet." />;
  }
  return (
    <div className="table-wrap">
      <table className="stack">
        <thead>
          <tr>
            <th scope="col">Run</th>
            <th scope="col">Branch</th>
            <th scope="col">Commit</th>
            <th scope="col">Started</th>
            <th scope="col" className="num">
              Tests
            </th>
            <th scope="col" className="num">
              Failed
            </th>
          </tr>
        </thead>
        <tbody>
          {runs.map((r) => (
            <tr key={r.id}>
              <td data-label="Run">
                <Link href={href({ name: 'run', slug, runId: r.id })}>
                  #{r.externalRunId}
                  {r.attempt > 1 ? ` (attempt ${r.attempt})` : ''}
                </Link>
              </td>
              <td data-label="Branch">
                <span>
                  {r.branch}
                  {r.pullRequest ? <span className="muted"> · PR #{r.pullRequest}</span> : null}
                </span>
              </td>
              <td data-label="Commit">
                <code>{shortSha(r.commitSha)}</code>
              </td>
              <td data-label="Started">{timeAgo(r.startedAt)}</td>
              <td data-label="Tests" className="num">{r.totals.tests}</td>
              <td data-label="Failed" className="num">{r.failedTests > 0 ? <span className="tone-text-bad">{r.failedTests}</span> : 0}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
