import { Link } from '../components/Link';
import { Pager } from '../components/Pager';
import { StatusBadge } from '../components/StatusBadge';
import { Empty, ErrorState, Loading } from '../components/States';
import { apiPath, type FailureGroupDetail, type FailureGroupPage } from '../lib/api';
import { plural, timeAgo } from '../lib/format';
import { useApi } from '../lib/useApi';
import { href, updateQuery, type Query } from '../lib/url';

/** Similar failures grouped together, so 40 timeouts read as one problem (US-05, FR-UI-005). */
export function FailureGroups({ slug, query }: { slug: string; query: Query }) {
  const page = useApi<FailureGroupPage>(apiPath(`/api/v1/projects/${slug}/failure-groups`, [['cursor', query.cursor]]));
  return (
    <>
      <h1>Failure groups</h1>
      <p className="muted">Failures with the same type, message (ignoring numbers, IDs and times) and top stack frames.</p>
      {page.status === 'loading' && <Loading what="failure groups" />}
      {page.status === 'error' && <ErrorState error={page.error} onRetry={page.reload} />}
      {page.status === 'ready' &&
        (page.data.items.length === 0 ? (
          <Empty title="No failures recorded yet." />
        ) : (
          <>
            <div className="table-wrap">
              <table className="stack">
                <thead>
                  <tr>
                    <th scope="col">Failure</th>
                    <th scope="col" className="num">
                      Times
                    </th>
                    <th scope="col" className="num">
                      Tests
                    </th>
                    <th scope="col">Last seen</th>
                  </tr>
                </thead>
                <tbody>
                  {page.data.items.map((g) => (
                    <tr key={g.id}>
                      <td data-label="Failure">
                        <Link href={href({ name: 'failure', slug, groupId: g.id })}>{g.normalizedMessage || '(no message)'}</Link>
                        <div className="muted small">
                          <code>{g.failureType}</code>
                        </div>
                      </td>
                      <td data-label="Times" className="num">{g.occurrences}</td>
                      <td data-label="Tests" className="num">{g.affectedTests}</td>
                      <td data-label="Last seen">{timeAgo(g.lastSeenAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager
              nextHref={page.data.nextCursor ? href({ name: 'failures', slug }, updateQuery(query, { cursor: page.data.nextCursor })) : null}
              firstHref={query.cursor ? href({ name: 'failures', slug }, updateQuery(query, { cursor: undefined })) : null}
            />
          </>
        ))}
    </>
  );
}

export function FailureGroupPage({ slug, groupId }: { slug: string; groupId: number }) {
  const group = useApi<FailureGroupDetail>(`/api/v1/projects/${slug}/failure-groups/${groupId}`);
  if (group.status === 'loading') {
    return <Loading what="the failure group" />;
  }
  if (group.status === 'error') {
    return <ErrorState error={group.error} onRetry={group.reload} />;
  }
  const g = group.data;
  return (
    <>
      <p className="breadcrumb">
        <Link href={href({ name: 'failures', slug })}>Failure groups</Link>
      </p>
      <h1 className="test-title">{g.normalizedMessage || '(no message)'}</h1>
      <p className="muted">
        <code>{g.failureType}</code> · {plural(g.occurrences, 'time')} · first seen {timeAgo(g.firstSeenAt)}, last seen{' '}
        {timeAgo(g.lastSeenAt)}
      </p>

      <h2>Example message</h2>
      <pre>{g.sampleMessage || '(empty)'}</pre>

      {g.topFrames.length > 0 && (
        <>
          <h2>Where it happens</h2>
          <ol className="frames">
            {g.topFrames.map((f) => (
              <li key={f}>
                <code>{f}</code>
              </li>
            ))}
          </ol>
        </>
      )}

      <h2>Affected tests</h2>
      <ul className="plain-list">
        {g.tests.map((t) => (
          <li key={t.id}>
            <Link href={href({ name: 'test', slug, testId: t.id })}>{t.testKey}</Link> <StatusBadge status={t.status} />
          </li>
        ))}
      </ul>
    </>
  );
}
