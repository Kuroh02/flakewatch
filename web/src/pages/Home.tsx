import { Link } from '../components/Link';
import { Empty, ErrorState, Loading } from '../components/States';
import type { ProjectPage } from '../lib/api';
import { useApi } from '../lib/useApi';
import { href } from '../lib/url';

/** Public projects, with a short explanation for first-time visitors (US-09). */
export function Home() {
  const projects = useApi<ProjectPage>('/api/v1/projects');

  return (
    <>
      <section className="intro">
        <h1>
          Is that red build real, or just a <em>flaky test</em>?
        </h1>
        <p>
          Flakewatch reads the test results your CI already produces. It tells you which failures are <strong>flaky</strong>{' '}
          (they passed and failed on the same code) and which are <strong>real</strong>, and shows the proof for every claim.
        </p>
      </section>

      <h2>Public projects</h2>
      {projects.status === 'loading' && <Loading what="projects" />}
      {projects.status === 'error' && <ErrorState error={projects.error} onRetry={projects.reload} />}
      {projects.status === 'ready' &&
        (projects.data.items.length === 0 ? (
          <Empty title="No public projects yet.">Create one with the bootstrap command, then upload results from CI.</Empty>
        ) : (
          <ul className="cards">
            {projects.data.items.map((p) => (
              <li key={p.slug} className="card">
                <Link href={href({ name: 'overview', slug: p.slug })} className="card-title">
                  {p.name}
                </Link>
                <p className="muted">
                  {p.slug} · default branch <code>{p.defaultBranch}</code>
                </p>
              </li>
            ))}
          </ul>
        ))}
    </>
  );
}
