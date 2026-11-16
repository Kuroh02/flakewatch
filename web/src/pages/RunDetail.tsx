import { Link } from '../components/Link';
import { RunLabelBadge } from '../components/StatusBadge';
import { Empty, ErrorState, Loading } from '../components/States';
import type { RunDetail as RunDetailData } from '../lib/api';
import { dateTime, shortSha } from '../lib/format';
import { useApi } from '../lib/useApi';
import { href } from '../lib/url';

/** One run, with each failure labelled so a developer knows what to look at first (US-02, FR-UI-004). */
export function RunDetail({ slug, runId }: { slug: string; runId: number }) {
  const run = useApi<RunDetailData>(`/api/v1/projects/${slug}/runs/${runId}`);
  if (run.status === 'loading') {
    return <Loading what="the run" />;
  }
  if (run.status === 'error') {
    return <ErrorState error={run.error} onRetry={run.reload} />;
  }
  const r = run.data;
  const newFailures = r.failures.filter((f) => f.label === 'NEW_FAILURE').length;
  const commitUrl = r.repositoryUrl ? `${r.repositoryUrl}/commit/${r.commitSha}` : null;

  return (
    <>
      <p className="breadcrumb">
        <Link href={href({ name: 'runs', slug })}>Runs</Link>
      </p>
      <h1>
        Run #{r.externalRunId}
        {r.attempt > 1 ? `, attempt ${r.attempt}` : ''}
      </h1>
      <p className="muted">
        {r.branch}
        {r.pullRequest ? ` · PR #${r.pullRequest}` : ''} ·{' '}
        {commitUrl ? (
          <a href={commitUrl} rel="noreferrer">
            <code>{shortSha(r.commitSha)}</code>
          </a>
        ) : (
          <code>{shortSha(r.commitSha)}</code>
        )}{' '}
        · started {dateTime(r.startedAt)}
      </p>

      <dl className="facts">
        <div>
          <dt>Tests</dt>
          <dd>{r.totals.tests}</dd>
        </div>
        <div>
          <dt>Passed</dt>
          <dd>{r.totals.passed}</dd>
        </div>
        <div>
          <dt>Failed</dt>
          <dd>{r.totals.failed + r.totals.errored}</dd>
        </div>
        <div>
          <dt>Retried</dt>
          <dd>{r.totals.retried}</dd>
        </div>
        <div>
          <dt>Skipped</dt>
          <dd>{r.totals.skipped}</dd>
        </div>
      </dl>

      <h2>Failures</h2>
      {r.failures.length === 0 ? (
        <Empty title="Every test passed in this run." />
      ) : (
        <>
          <p className="verdict">
            {newFailures === 0
              ? 'No new failures: everything that failed here is already known to be flaky or broken.'
              : `${newFailures} new failure${newFailures === 1 ? '' : 's'}: worth a look.`}
          </p>
          <div className="table-wrap">
            <table className="stack">
              <thead>
                <tr>
                  <th scope="col">Test</th>
                  <th scope="col">Verdict</th>
                  <th scope="col">Message</th>
                  <th scope="col">Job</th>
                </tr>
              </thead>
              <tbody>
                {r.failures.map((f) => (
                  <tr key={`${f.testId}-${f.jobKey}`}>
                    <td data-label="Test">
                      <Link href={href({ name: 'test', slug, testId: f.testId })}>{f.testKey}</Link>
                    </td>
                    <td data-label="Verdict">
                      <RunLabelBadge label={f.label} />
                    </td>
                    <td data-label="Message" className="message">{f.message ?? '—'}</td>
                    <td data-label="Job">
                      <span>
                        {f.jobKey}
                        {f.variant ? <span className="muted"> · {f.variant}</span> : null}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <h2>Uploads</h2>
      <ul className="plain-list">
        {r.uploads.map((u) => (
          <li key={u.uploadId}>
            {u.jobKey}
            {u.variant ? ` (${u.variant})` : ''}: {u.status.toLowerCase()}
          </li>
        ))}
      </ul>
    </>
  );
}
