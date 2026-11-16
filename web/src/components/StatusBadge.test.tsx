import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusBadge } from './StatusBadge';

describe('NFR-A11Y-001: status badges', () => {
  it('shows the status as text with an icon, and explains it (US-03 #2)', () => {
    render(<StatusBadge status="SUSPECTED" />);
    const badge = screen.getByText('Suspected');
    expect(badge.closest('[title]')).toHaveAttribute(
      'title',
      'Failed and recovered on its own at least twice. Not proven yet.',
    );
    expect(screen.getByText('?')).toHaveAttribute('aria-hidden', 'true');
  });
});
