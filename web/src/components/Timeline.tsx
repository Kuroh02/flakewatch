import { dateTime, shortSha } from '../lib/format';
import { outcomeInfo } from '../lib/status';
import type { TestDetail } from '../lib/api';

type Point = TestDetail['timeline'][number];

/**
 * The last results of a test, oldest first. Each outcome has its own shape as well as colour:
 * ● passed, ✕ failed, ◐ passed on retry, ○ skipped. A hidden table carries the same data for
 * screen readers (NFR-A11Y-001).
 */
export function Timeline({ points }: { points: Point[] }) {
  return (
    <figure className="timeline">
      <div className="timeline-strip" aria-hidden="true">
        {points.map((p, i) => {
          const info = outcomeInfo(p.outcome);
          return (
            <span
              key={`${p.runId}-${p.variant}-${i}`}
              className={`timeline-point shape-${info.shape} tone-${info.tone}`}
              title={`${info.label} · ${dateTime(p.executedAt)} · ${p.branch} @ ${shortSha(p.commitSha)}${p.variant ? ` · ${p.variant}` : ''}`}
            />
          );
        })}
      </div>
      <figcaption className="legend">
        <span><span className="timeline-point shape-dot tone-good" aria-hidden="true" /> Passed</span>
        <span><span className="timeline-point shape-cross tone-bad" aria-hidden="true" /> Failed</span>
        <span><span className="timeline-point shape-half tone-warn" aria-hidden="true" /> Passed on retry</span>
        <span><span className="timeline-point shape-ring tone-neutral" aria-hidden="true" /> Skipped</span>
      </figcaption>
      <div className="visually-hidden">
        <table>
        <caption>Results, oldest first</caption>
        <thead>
          <tr>
            <th>When</th>
            <th>Result</th>
            <th>Branch</th>
            <th>Commit</th>
            <th>Variant</th>
          </tr>
        </thead>
        <tbody>
          {points.map((p, i) => (
            <tr key={`${p.runId}-${p.variant}-${i}`}>
              <td>{dateTime(p.executedAt)}</td>
              <td>{outcomeInfo(p.outcome).label}</td>
              <td>{p.branch}</td>
              <td>{shortSha(p.commitSha)}</td>
              <td>{p.variant || 'default'}</td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>
    </figure>
  );
}
