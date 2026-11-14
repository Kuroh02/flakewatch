import { useEffect, useState } from 'react';

interface VersionInfo {
  version: string;
  commit: string;
  builtAt: string;
}

export default function App() {
  const [versionInfo, setVersionInfo] = useState<VersionInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    fetch('/api/v1/version')
      .then(async (res) => {
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        return res.json() as Promise<VersionInfo>;
      })
      .then((data) => {
        if (isMounted) {
          setVersionInfo(data);
          setLoading(false);
        }
      })
      .catch((err: Error) => {
        if (isMounted) {
          setError(err.message || 'Failed to fetch');
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <main
      style={{
        fontFamily: 'system-ui, sans-serif',
        padding: '2rem',
        maxWidth: '600px',
        margin: '0 auto',
      }}
    >
      <h1>Flakewatch</h1>
      <p>Test suite health and flaky test detection platform.</p>

      <section
        style={{
          border: '1px solid #ccc',
          borderRadius: '8px',
          padding: '1rem',
          marginTop: '1.5rem',
        }}
      >
        <h2>Backend Status</h2>
        {loading && <p>Loading version info...</p>}
        {error && (
          <p style={{ color: 'red' }}>Failed to load version: {error}</p>
        )}
        {versionInfo && (
          <dl
            style={{
              display: 'grid',
              gridTemplateColumns: 'auto 1fr',
              gap: '0.5rem 1rem',
            }}
          >
            <dt>
              <strong>Version:</strong>
            </dt>
            <dd>
              <code>{versionInfo.version}</code>
            </dd>

            <dt>
              <strong>Commit:</strong>
            </dt>
            <dd>
              <code>{versionInfo.commit}</code>
            </dd>

            <dt>
              <strong>Built At:</strong>
            </dt>
            <dd>{versionInfo.builtAt}</dd>
          </dl>
        )}
      </section>
    </main>
  );
}
