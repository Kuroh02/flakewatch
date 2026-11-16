import { useEffect, useState } from 'react';
import { Link } from '../components/Link';
import { Pager } from '../components/Pager';
import { StatusBadge } from '../components/StatusBadge';
import { Empty, ErrorState, Loading } from '../components/States';
import { apiPath, type TestSummaryPage } from '../lib/api';
import { percent, timeAgo } from '../lib/format';
import { navigate } from '../lib/router';
import { statusInfo, STATUS_ORDER } from '../lib/status';
import { useApi } from '../lib/useApi';
import { href, updateQuery, type Query } from '../lib/url';

/** Tests ranked by impact, filtered by status and name. Every filter lives in the URL (US-03, FR-UI-002). */
export function TestsList({ slug, query }: { slug: string; query: Query }) {
  const sort = query.sort ?? 'impact';
  const page = useApi<TestSummaryPage>(
    apiPath(`/api/v1/projects/${slug}/tests`, [
      ['status', query.status],
      ['q', query.q],
      ['sort', sort],
      ['cursor', query.cursor],
    ]),
  );
  const [search, setSearch] = useState(query.q ?? '');

  // Keep the box in step when the address changes (back button, shared link)
  useEffect(() => setSearch(query.q ?? ''), [query.q]);

  // Search as you type, without flooding the server: apply the text after a short pause
  useEffect(() => {
    if (search === (query.q ?? '')) {
      return;
    }
    const timer = setTimeout(() => go({ q: search.trim() || undefined }, true), 350);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  function go(changes: Record<string, string | undefined>, replace = false) {
    navigate(href({ name: 'tests', slug }, updateQuery(query, changes)), { replace });
  }

  const statusName = query.status ? statusInfo(query.status).label.toLowerCase() : '';

  return (
    <>
      <h1>Tests</h1>
      <form className="filters" role="search" onSubmit={(e) => e.preventDefault()}>
        <label>
          Status
          <select value={query.status ?? ''} onChange={(e) => go({ status: e.target.value || undefined })}>
            <option value="">All statuses</option>
            {STATUS_ORDER.map((s) => (
              <option key={s} value={s}>
                {statusInfo(s).label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Search
          <input type="search" value={search} placeholder="Test name or class" onChange={(e) => setSearch(e.target.value)} />
        </label>
        <label>
          Sort by
          <select value={sort} onChange={(e) => go({ sort: e.target.value === 'impact' ? undefined : e.target.value })}>
            <option value="impact">Impact (worst first)</option>
            <option value="recent">Most recent failure</option>
            <option value="name">Name</option>
          </select>
        </label>
      </form>

      {page.status === 'loading' && <Loading what="tests" />}
      {page.status === 'error' && <ErrorState error={page.error} onRetry={page.reload} />}
      {page.status === 'ready' &&
        (page.data.items.length === 0 ? (
          <Empty title={`No tests match${statusName ? ` "${statusName}"` : ''}${query.q ? ` and "${query.q}"` : ''}.`}>
            {query.status === 'FLAKY'
              ? 'A test is only marked flaky when there is proof: it passed and failed on the same code.'
              : 'Try another filter.'}
          </Empty>
        ) : (
          <>
            <div className="table-wrap">
              <table className="stack">
                <thead>
                  <tr>
                    <th scope="col">Test</th>
                    <th scope="col">Status</th>
                    <th scope="col" className="num" title="Failures plus retries in the last 14 days">
                      Impact
                    </th>
                    <th scope="col" className="num" title="How often the result changes between runs">
                      Flip rate
                    </th>
                    <th scope="col">Last failure</th>
                  </tr>
                </thead>
                <tbody>
                  {page.data.items.map((t) => (
                    <tr key={t.id}>
                      <td data-label="Test">
                        <Link href={href({ name: 'test', slug, testId: t.id })}>{t.name}</Link>
                        <div className="muted small">{t.suite}</div>
                      </td>
                      <td data-label="Status">
                        <StatusBadge status={t.status} />
                      </td>
                      <td data-label="Impact" className="num">{t.impact}</td>
                      <td data-label="Flip rate" className="num">{percent(t.flipRate)}</td>
                      <td data-label="Last failure">{t.lastFailureAt ? timeAgo(t.lastFailureAt) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager
              nextHref={page.data.nextCursor ? href({ name: 'tests', slug }, updateQuery(query, { cursor: page.data.nextCursor })) : null}
              firstHref={query.cursor ? href({ name: 'tests', slug }, updateQuery(query, { cursor: undefined })) : null}
            />
          </>
        ))}
    </>
  );
}
