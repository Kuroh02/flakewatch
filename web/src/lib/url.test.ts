import { describe, expect, it } from 'vitest';
import { href, matchRoute, readQuery, updateQuery } from './url';

describe('FR-UI-007: every view has a shareable URL', () => {
  it('reads the dashboard routes', () => {
    expect(matchRoute('/')).toEqual({ name: 'home' });
    expect(matchRoute('/p/demo')).toEqual({ name: 'overview', slug: 'demo' });
    expect(matchRoute('/p/demo/tests')).toEqual({ name: 'tests', slug: 'demo' });
    expect(matchRoute('/p/demo/tests/42')).toEqual({ name: 'test', slug: 'demo', testId: 42 });
    expect(matchRoute('/p/demo/runs')).toEqual({ name: 'runs', slug: 'demo' });
    expect(matchRoute('/p/demo/runs/7')).toEqual({ name: 'run', slug: 'demo', runId: 7 });
    expect(matchRoute('/p/demo/failures')).toEqual({ name: 'failures', slug: 'demo' });
    expect(matchRoute('/p/demo/failures/3')).toEqual({ name: 'failure', slug: 'demo', groupId: 3 });
  });

  it('accepts a trailing slash, as people often paste links that way', () => {
    expect(matchRoute('/p/demo/tests/')).toEqual({ name: 'tests', slug: 'demo' });
  });

  it('shows "not found" for unknown pages and non-numeric IDs', () => {
    expect(matchRoute('/p/demo/settings')).toEqual({ name: 'notFound' });
    expect(matchRoute('/p/demo/tests/abc')).toEqual({ name: 'notFound' });
    expect(matchRoute('/admin')).toEqual({ name: 'notFound' });
  });

  it('builds a link to the flaky tests list that keeps its filter when shared (US-03 #3)', () => {
    expect(href({ name: 'tests', slug: 'demo' }, { status: 'FLAKY' })).toBe('/p/demo/tests?status=FLAKY');
    expect(href({ name: 'test', slug: 'demo', testId: 42 })).toBe('/p/demo/tests/42');
    expect(href({ name: 'home' })).toBe('/');
  });

  it('leaves empty filters out of the URL', () => {
    expect(href({ name: 'tests', slug: 'demo' }, { status: '', q: undefined, sort: 'impact' })).toBe(
      '/p/demo/tests?sort=impact',
    );
  });

  it('encodes search text with spaces and symbols', () => {
    const link = href({ name: 'tests', slug: 'demo' }, { q: 'Cart Test#adds' });
    expect(link).toBe('/p/demo/tests?q=Cart+Test%23adds');
    expect(readQuery(link.split('?')[1] ?? '')).toEqual({ q: 'Cart Test#adds' });
  });

  it('goes back to the first page when a filter changes, but not when only the page changes', () => {
    const current = { status: 'FLAKY', cursor: 'bzo1MA' };
    expect(updateQuery(current, { status: 'BROKEN' })).toEqual({ status: 'BROKEN' });
    expect(updateQuery(current, { cursor: 'bzoxMDA' })).toEqual({ status: 'FLAKY', cursor: 'bzoxMDA' });
    expect(updateQuery(current, { status: undefined })).toEqual({});
  });
});
