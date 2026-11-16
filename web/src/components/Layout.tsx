import type { ReactNode } from 'react';
import { href, type Route } from '../lib/url';
import { Link } from './Link';

/** Page frame: header, and the project's section tabs when inside a project. */
export function Layout({ route, projectName, children }: { route: Route; projectName?: string; children: ReactNode }) {
  const slug = 'slug' in route ? route.slug : null;
  const section = route.name === 'test' ? 'tests' : route.name === 'run' ? 'runs' : route.name === 'failure' ? 'failures' : route.name;
  const tabs: Array<[string, Route]> = slug
    ? [
        ['Overview', { name: 'overview', slug }],
        ['Tests', { name: 'tests', slug }],
        ['Runs', { name: 'runs', slug }],
        ['Failure groups', { name: 'failures', slug }],
      ]
    : [];

  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="site-header">
        <div className="container header-row">
          <Link href="/" className="brand">
            <svg className="brand-mark" viewBox="0 0 32 16" aria-hidden="true">
              <path d="M1 8 H7 L9 3 L11 13 L13 6 L15 10 L17 8 H31" />
            </svg>
            Flakewatch
          </Link>
          {slug && <span className="project-name">{projectName ?? slug}</span>}
        </div>
        {tabs.length > 0 && (
          <nav className="container tabs" aria-label="Project sections">
            {tabs.map(([label, tab]) => (
              <Link key={label} href={href(tab)} className="tab" aria-current={tab.name === section ? 'page' : undefined}>
                {label}
              </Link>
            ))}
          </nav>
        )}
      </header>
      <main id="main" className="container">
        {children}
      </main>
    </>
  );
}
