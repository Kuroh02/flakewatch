/** Wording and symbols for statuses. Text and shape always carry the meaning; colour only helps (NFR-A11Y-001). */

export type TestStatus = 'STABLE' | 'FLAKY' | 'SUSPECTED' | 'FAILING' | 'BROKEN' | 'NOT_ENOUGH_DATA';
export type Tone = 'good' | 'flaky' | 'warn' | 'bad' | 'neutral';

export interface Info {
  label: string;
  explanation: string;
  icon: string;
  tone: Tone;
}

/** SPEC §9, word for word. */
const STATUS: Record<TestStatus, Info> = {
  STABLE: { label: 'Stable', explanation: 'No sign of flakiness and passing.', icon: '✓', tone: 'good' },
  FLAKY: { label: 'Flaky', explanation: 'Proven: it passed and failed on the same code.', icon: '≈', tone: 'flaky' },
  SUSPECTED: {
    label: 'Suspected',
    explanation: 'Failed and recovered on its own at least twice. Not proven yet.',
    icon: '?',
    tone: 'warn',
  },
  FAILING: {
    label: 'Failing',
    explanation: 'Failed on the latest default-branch run. May be a real failure.',
    icon: '!',
    tone: 'bad',
  },
  BROKEN: { label: 'Broken', explanation: 'Failed on the last 3 default-branch runs.', icon: '✕', tone: 'bad' },
  NOT_ENOUGH_DATA: { label: 'Not enough data', explanation: 'Fewer than 3 results so far.', icon: '…', tone: 'neutral' },
};

export const STATUS_ORDER: TestStatus[] = ['BROKEN', 'FLAKY', 'FAILING', 'SUSPECTED', 'STABLE', 'NOT_ENOUGH_DATA'];

export function statusInfo(status: string): Info {
  return STATUS[status as TestStatus] ?? { label: status, explanation: '', icon: '•', tone: 'neutral' };
}

const RUN_LABELS: Record<string, Info> = {
  KNOWN_FLAKY: { label: 'Known flaky', explanation: 'This test is proven flaky. Probably not your change.', icon: '≈', tone: 'flaky' },
  NEW_FAILURE: { label: 'New failure', explanation: 'Not known to be flaky. Worth a look.', icon: '!', tone: 'bad' },
  BROKEN: { label: 'Broken', explanation: 'This test has been failing on the default branch.', icon: '✕', tone: 'bad' },
  PASSED_ON_RETRY: { label: 'Passed on retry', explanation: 'Failed first, then passed when re-run.', icon: '◐', tone: 'flaky' },
  QUARANTINED: { label: 'Quarantined', explanation: 'Failure muted by quarantine; still tracked.', icon: '⏸', tone: 'neutral' },
};

export function runLabelInfo(label: string): Info {
  return RUN_LABELS[label] ?? { label, explanation: '', icon: '•', tone: 'neutral' };
}

export interface OutcomeInfo {
  label: string;
  shape: 'dot' | 'cross' | 'half' | 'ring';
  tone: Tone;
}

const OUTCOMES: Record<string, OutcomeInfo> = {
  PASSED: { label: 'Passed', shape: 'dot', tone: 'good' },
  FAILED: { label: 'Failed', shape: 'cross', tone: 'bad' },
  PASSED_ON_RETRY: { label: 'Passed on retry', shape: 'half', tone: 'flaky' },
  SKIPPED: { label: 'Skipped', shape: 'ring', tone: 'neutral' },
};

export function outcomeInfo(outcome: string): OutcomeInfo {
  return OUTCOMES[outcome] ?? { label: outcome, shape: 'ring', tone: 'neutral' };
}
