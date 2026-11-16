/** Every page of the dashboard. The URL is the single source of truth, so any view can be shared (FR-UI-007). */
export type Route =
  | { name: 'home' }
  | { name: 'overview'; slug: string }
  | { name: 'tests'; slug: string }
  | { name: 'test'; slug: string; testId: number }
  | { name: 'runs'; slug: string }
  | { name: 'run'; slug: string; runId: number }
  | { name: 'failures'; slug: string }
  | { name: 'failure'; slug: string; groupId: number }
  | { name: 'notFound' };

export type Query = Record<string, string>;

const SLUG = '([a-z0-9][a-z0-9-]{1,38}[a-z0-9])';
const ID = '(\\d{1,18})';

const PATTERNS: Array<[RegExp, (m: RegExpMatchArray) => Route]> = [
  [/^\/$/, () => ({ name: 'home' })],
  [new RegExp(`^/p/${SLUG}$`), (m) => ({ name: 'overview', slug: m[1]! })],
  [new RegExp(`^/p/${SLUG}/tests$`), (m) => ({ name: 'tests', slug: m[1]! })],
  [new RegExp(`^/p/${SLUG}/tests/${ID}$`), (m) => ({ name: 'test', slug: m[1]!, testId: Number(m[2]) })],
  [new RegExp(`^/p/${SLUG}/runs$`), (m) => ({ name: 'runs', slug: m[1]! })],
  [new RegExp(`^/p/${SLUG}/runs/${ID}$`), (m) => ({ name: 'run', slug: m[1]!, runId: Number(m[2]) })],
  [new RegExp(`^/p/${SLUG}/failures$`), (m) => ({ name: 'failures', slug: m[1]! })],
  [new RegExp(`^/p/${SLUG}/failures/${ID}$`), (m) => ({ name: 'failure', slug: m[1]!, groupId: Number(m[2]) })],
];

export function matchRoute(pathname: string): Route {
  const path = pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname;
  for (const [pattern, build] of PATTERNS) {
    const m = path.match(pattern);
    if (m) {
      return build(m);
    }
  }
  return { name: 'notFound' };
}

function pathOf(route: Route): string {
  switch (route.name) {
    case 'home':
    case 'notFound':
      return '/';
    case 'overview':
      return `/p/${route.slug}`;
    case 'tests':
      return `/p/${route.slug}/tests`;
    case 'test':
      return `/p/${route.slug}/tests/${route.testId}`;
    case 'runs':
      return `/p/${route.slug}/runs`;
    case 'run':
      return `/p/${route.slug}/runs/${route.runId}`;
    case 'failures':
      return `/p/${route.slug}/failures`;
    case 'failure':
      return `/p/${route.slug}/failures/${route.groupId}`;
  }
}

/** A link to a page, with only the non-empty query values. */
export function href(route: Route, query: Record<string, string | undefined> = {}): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') {
      params.set(key, value);
    }
  }
  const search = params.toString();
  return search ? `${pathOf(route)}?${search}` : pathOf(route);
}

export function readQuery(search: string): Query {
  const query: Query = {};
  new URLSearchParams(search).forEach((value, key) => {
    query[key] = value;
  });
  return query;
}

/**
 * Applies changes to the current query. Changing any filter goes back to the first page,
 * because the old cursor belongs to the old result list.
 */
export function updateQuery(current: Query, changes: Record<string, string | undefined>): Query {
  const next: Query = { ...current };
  const filterChanged = Object.keys(changes).some((key) => key !== 'cursor' && changes[key] !== current[key]);
  if (filterChanged) {
    delete next.cursor;
  }
  for (const [key, value] of Object.entries(changes)) {
    if (value === undefined || value === '') {
      delete next[key];
    } else {
      next[key] = value;
    }
  }
  return next;
}
