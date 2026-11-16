import { runLabelInfo, statusInfo, type Info } from '../lib/status';

function Badge({ info }: { info: Info }) {
  return (
    <span className={`badge tone-${info.tone}`} title={info.explanation}>
      <span className="badge-icon" aria-hidden="true">
        {info.icon}
      </span>
      <span>{info.label}</span>
    </span>
  );
}

/** A test status: icon, text and colour together, with the explanation as a tooltip (NFR-A11Y-001). */
export function StatusBadge({ status }: { status: string }) {
  return <Badge info={statusInfo(status)} />;
}

/** How a failure in a run should be read (US-02). */
export function RunLabelBadge({ label }: { label: string }) {
  return <Badge info={runLabelInfo(label)} />;
}
