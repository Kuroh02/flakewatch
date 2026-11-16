import { describe, expect, it } from 'vitest';
import { duration, percent, plural, shortSha, timeAgo } from './format';

const now = new Date('2026-11-16T12:00:00Z');

describe('Human-friendly numbers and times', () => {
  it('shows how long ago a run happened', () => {
    expect(timeAgo('2026-11-16T11:59:40Z', now)).toBe('just now');
    expect(timeAgo('2026-11-16T11:55:00Z', now)).toBe('5 minutes ago');
    expect(timeAgo('2026-11-16T11:00:00Z', now)).toBe('1 hour ago');
    expect(timeAgo('2026-11-16T09:00:00Z', now)).toBe('3 hours ago');
    expect(timeAgo('2026-11-15T10:00:00Z', now)).toBe('yesterday');
    expect(timeAgo('2026-11-12T12:00:00Z', now)).toBe('4 days ago');
    expect(timeAgo('2026-10-01T12:00:00Z', now)).toBe('1 Oct 2026');
  });

  it('treats a timestamp slightly in the future (clock skew) as just now', () => {
    expect(timeAgo('2026-11-16T12:00:30Z', now)).toBe('just now');
  });

  it('formats test durations the way people read them', () => {
    expect(duration(0)).toBe('0 ms');
    expect(duration(850)).toBe('850 ms');
    expect(duration(1234)).toBe('1.2 s');
    expect(duration(65_000)).toBe('1 min 5 s');
  });

  it('shortens commit SHAs to 7 characters, like GitHub', () => {
    expect(shortSha('3f2a9c1d8e7b6a5f4e3d')).toBe('3f2a9c1');
    expect(shortSha('abc')).toBe('abc');
  });

  it('shows rates as whole percentages', () => {
    expect(percent(0)).toBe('0%');
    expect(percent(0.6667)).toBe('67%');
    expect(percent(1)).toBe('100%');
  });

  it('uses the right plural', () => {
    expect(plural(1, 'test')).toBe('1 test');
    expect(plural(3, 'test')).toBe('3 tests');
    expect(plural(0, 'failure')).toBe('0 failures');
  });
});
