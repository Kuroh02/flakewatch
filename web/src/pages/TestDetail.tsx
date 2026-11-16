import { Link } from '../components/Link';
import { StatusBadge } from '../components/StatusBadge';
import { Empty, ErrorState, Loading } from '../components/States';
import { Timeline } from '../components/Timeline';
import type { Evidence, ExecutionPage, TestDetail as TestDetailData } from '../lib/api';
import { dateTime, duration, percent, shortSha, timeAgo } from '../lib/format';
import { statusInfo } from '../lib/status';
import { useApi } from '../lib/useApi';
import { href } from '../lib/url';

const EVIDENCE_KIND: Record<string, string> = {
  RETRY_PASS: 'Passed on retry',
  SAME_COMMIT_MIXED: 'Same commit, different results',
};

/** One test: its status per variant, the proof behind it, and its recent history (US-04, FR-UI-003). */
export function TestDetail({ slug, testId }: { slug: string; testId: number }) {
  const test = useApi<TestDetailData>(`/api/v1/projects/${slug}/tests/${testId}`);
  const executions = useApi<ExecutionPage>(`/api/v1/projects/${slug}/tests/${testId}/executions?limit=50`);

  if (test.status === 'loading') {
    return <Loading what="the test" />;
  }
  if (test.status === 'error') {
    return <ErrorState error={test.error} onRetry={test.reload} />;
  }
  const t = test.data;
  const recentFailures =
    executions.status === 'ready' ? executions.data.items.filter((e) => e.status === 'FAILED' || e.status === 'ERRORED').slice(0, 5) : [];
  const showVariants = t.variants.length > 1 || t.variants.some((v) => v.variant !== '');
  const latestDuration = t.durationTrend[t.durationTrend.length - 1];

  return (
    <>
      <p className="breadcrumb">
        <Link href={href({ name: 'tests', slug })}>Tests</Link> / {t.suite}
      </p>
      <h1 className="test-title">{t.name}</h1>
      <div className="headline">
        <StatusBadge status={t.status} />
        <span className="muted">
          {statusInfo(t.status).explanation} Since {timeAgo(t.statusSince)}.
        </span>
      </div>

      <dl className="facts">
        <div>
          <dt>Impact</dt>
          <dd>{t.impact}</dd>
        </div>
        <div>
          <dt>Flip rate</dt>
          <dd>{percent(t.flipRate)}</dd>
        </div>
        <div>
          <dt>Last failure</dt>
          <dd>{t.lastFailureAt ? timeAgo(t.lastFailureAt) : 'None in the window'}</dd>
        </div>
        {latestDuration && (
          <div>
            <dt>Typical duration</dt>
            <dd>{duration(latestDuration.averageMs)}</dd>
          </div>
        )}
      </dl>

      {showVariants && (
        <section>
          <h2>By environment</h2>
          <ul className="variants">
            {t.variants.map((v) => (
              <li key={v.variant}>
                <span className="variant-name">{v.variant || 'default'}</span> <StatusBadge status={v.status} />
              </li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <h2>Evidence</h2>
        {t.evidence.length === 0 ? (
          <p className="muted">
            {t.status === 'SUSPECTED'
              ? 'No proof yet. It failed and recovered on its own, but never passed and failed on the same code.'
              : 'No proof of flakiness in the last 14 days.'}
          </p>
        ) : (
          <ul className="evidence">
            {t.evidence.map((e, i) => (
              <EvidenceItem key={i} slug={slug} evidence={e} />
            ))}
          </ul>
        )}
      </section>

      <section>
        <h2>Last {t.timeline.length} results</h2>
        {t.timeline.length === 0 ? <p className="muted">No results yet.</p> : <Timeline points={t.timeline} />}
      </section>

      {t.failureGroups.length > 0 && (
        <section>
          <h2>How it fails</h2>
          <ul className="plain-list">
            {t.failureGroups.map((g) => (
              <li key={g.id}>
                <Link href={href({ name: 'failure', slug, groupId: g.id })}>
                  <code>{g.failureType}</code>: {g.normalizedMessage}
                </Link>{' '}
                <span className="muted">({g.occurrences} times across all tests)</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {recentFailures.length > 0 && (
        <section>
          <h2>Recent failure messages</h2>
          {recentFailures.map((e) => (
            <details key={e.id} className="failure">
              <summary>
                {dateTime(e.executedAt)} · {e.branch} @ {shortSha(e.commitSha)}
                {e.executionIndex > 0 ? ` · retry ${e.executionIndex}` : ''} — {e.failureMessage ?? e.failureType ?? 'no message'}
              </summary>
              {e.stackTrace ? <pre>{e.stackTrace}</pre> : <p className="muted">No stack trace was reported.</p>}
            </details>
          ))}
        </section>
      )}
      {executions.status === 'ready' && executions.data.items.length === 0 && <Empty title="No executions stored for this test." />}
    </>
  );
}

function EvidenceItem({ slug, evidence }: { slug: string; evidence: Evidence }) {
  return (
    <li>
      <strong>{EVIDENCE_KIND[evidence.kind] ?? evidence.kind}</strong>
      {evidence.variant && <span className="muted"> · {evidence.variant}</span>}
      <div>{evidence.explanation}</div>
      <div className="muted small">
        {timeAgo(evidence.observedAt)} ·{' '}
        {evidence.runIds.map((id, i) => (
          <span key={id}>
            {i > 0 && ', '}
            <Link href={href({ name: 'run', slug, runId: id })}>run {id}</Link>
          </span>
        ))}
      </div>
    </li>
  );
}
