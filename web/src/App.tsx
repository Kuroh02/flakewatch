import { Layout } from './components/Layout';
import { ErrorState, Loading, NotFound } from './components/States';
import type { Project } from './lib/api';
import { useLocation } from './lib/router';
import { useApi } from './lib/useApi';
import { matchRoute, readQuery, type Route } from './lib/url';
import { FailureGroupPage, FailureGroups } from './pages/FailureGroups';
import { Home } from './pages/Home';
import { Overview } from './pages/Overview';
import { RunDetail } from './pages/RunDetail';
import { RunsList } from './pages/RunsList';
import { TestDetail } from './pages/TestDetail';
import { TestsList } from './pages/TestsList';

export default function App() {
  const { pathname, search } = useLocation();
  const route = matchRoute(pathname);
  const query = readQuery(search);

  if (route.name === 'home') {
    return (
      <Layout route={route}>
        <Home />
      </Layout>
    );
  }
  if (route.name === 'notFound') {
    return (
      <Layout route={route}>
        <NotFound />
      </Layout>
    );
  }
  return <ProjectPage route={route} query={query} />;
}

/** Every page inside a project first checks the project exists and is visible (FR-PRJ-004). */
function ProjectPage({ route, query }: { route: Extract<Route, { slug: string }>; query: Record<string, string> }) {
  const project = useApi<Project>(`/api/v1/projects/${route.slug}`);

  let content;
  if (project.status === 'loading') {
    content = <Loading what="the project" />;
  } else if (project.status === 'error') {
    content = project.error.status === 404 ? <NotFound /> : <ErrorState error={project.error} onRetry={project.reload} />;
  } else {
    switch (route.name) {
      case 'overview':
        content = <Overview slug={route.slug} />;
        break;
      case 'tests':
        content = <TestsList slug={route.slug} query={query} />;
        break;
      case 'test':
        content = <TestDetail key={route.testId} slug={route.slug} testId={route.testId} />;
        break;
      case 'runs':
        content = <RunsList slug={route.slug} query={query} />;
        break;
      case 'run':
        content = <RunDetail key={route.runId} slug={route.slug} runId={route.runId} />;
        break;
      case 'failures':
        content = <FailureGroups slug={route.slug} query={query} />;
        break;
      case 'failure':
        content = <FailureGroupPage key={route.groupId} slug={route.slug} groupId={route.groupId} />;
        break;
    }
  }

  return (
    <Layout route={route} projectName={project.status === 'ready' ? project.data.name : undefined}>
      {content}
    </Layout>
  );
}
