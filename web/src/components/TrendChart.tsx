import type { ProjectSummary } from '../lib/api';

type Point = ProjectSummary['flakyTrend'][number];

const WIDTH = 600;
const HEIGHT = 120;
const INSET = 12;

/** Flaky-test count per day for the last 30 days (FR-UI-001), with a hidden table for screen readers. */
export function TrendChart({ points }: { points: Point[] }) {
  const max = Math.max(1, ...points.map((p) => p.flaky));
  const step = points.length > 1 ? (WIDTH - 2 * INSET) / (points.length - 1) : 0;
  const coords = points.map(
    (p, i) => [points.length > 1 ? INSET + i * step : WIDTH / 2, HEIGHT - 10 - (p.flaky / max) * (HEIGHT - 28)] as const,
  );
  const line = coords.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const latest = points[points.length - 1];

  return (
    <figure className="trend">
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label={`Flaky tests per day; latest: ${latest?.flaky ?? 0}`}>
        <line x1={INSET} y1={HEIGHT - 10} x2={WIDTH - INSET} y2={HEIGHT - 10} className="trend-axis" />
        {coords.length > 1 && <path d={line} className="trend-line" />}
        {coords.map(([x, y], i) => (
          <circle key={points[i]!.day} cx={x} cy={y} r={3} className="trend-dot">
            <title>{`${points[i]!.day}: ${points[i]!.flaky} flaky`}</title>
          </circle>
        ))}
      </svg>
      <div className="visually-hidden">
        <table>
        <caption>Flaky, suspected and broken tests per day</caption>
        <thead>
          <tr>
            <th>Day</th>
            <th>Flaky</th>
            <th>Suspected</th>
            <th>Broken</th>
          </tr>
        </thead>
        <tbody>
          {points.map((p) => (
            <tr key={p.day}>
              <td>{p.day}</td>
              <td>{p.flaky}</td>
              <td>{p.suspected}</td>
              <td>{p.broken}</td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </figure>
  );
}
