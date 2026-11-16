import { describe, expect, it } from 'vitest';
import { outcomeInfo, runLabelInfo, statusInfo } from './status';

describe('NFR-A11Y-001, SPEC §9: statuses are explained in words, never by colour alone', () => {
  it('uses the exact one-line explanations from the spec', () => {
    expect(statusInfo('FLAKY').explanation).toBe('Proven: it passed and failed on the same code.');
    expect(statusInfo('SUSPECTED').explanation).toBe('Failed and recovered on its own at least twice. Not proven yet.');
    expect(statusInfo('BROKEN').explanation).toBe('Failed on the last 3 default-branch runs.');
    expect(statusInfo('NOT_ENOUGH_DATA').label).toBe('Not enough data');
  });

  it('gives every status a text label and an icon', () => {
    for (const s of ['STABLE', 'FLAKY', 'SUSPECTED', 'FAILING', 'BROKEN', 'NOT_ENOUGH_DATA'] as const) {
      expect(statusInfo(s).label.length > 0).toBe(true);
      expect(statusInfo(s).icon.length > 0).toBe(true);
    }
  });

  it('labels failures in a run the way US-02 describes them', () => {
    expect(runLabelInfo('KNOWN_FLAKY').label).toBe('Known flaky');
    expect(runLabelInfo('NEW_FAILURE').label).toBe('New failure');
    expect(runLabelInfo('BROKEN').label).toBe('Broken');
    expect(runLabelInfo('PASSED_ON_RETRY').label).toBe('Passed on retry');
  });

  it('draws timeline points with a different shape for each outcome', () => {
    const shapes = ['PASSED', 'FAILED', 'PASSED_ON_RETRY', 'SKIPPED'].map((o) => outcomeInfo(o).shape);
    expect(new Set(shapes).size).toBe(4);
    expect(outcomeInfo('PASSED_ON_RETRY').label).toBe('Passed on retry');
  });
});
