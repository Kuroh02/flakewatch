import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { mockApi, openAt } from '../test/mockApi';
import { demoProject, flakyCheckout, suspectedSearch } from '../test/sampleData';

describe('US-03: ranked list of flaky tests', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('opens a shared link with its filter applied, worst test first', async () => {
    const fetchMock = mockApi({
      '/api/v1/projects/demo': demoProject,
      '/api/v1/projects/demo/tests': { items: [flakyCheckout], nextCursor: null },
    });
    openAt('/p/demo/tests?status=FLAKY');
    render(<App />);

    expect(await screen.findByRole('link', { name: 'paysWithCard' })).toHaveAttribute('href', '/p/demo/tests/42');
    const requested = fetchMock.mock.calls.map((call) => String(call[0]));
    expect(requested).toContain('/api/v1/projects/demo/tests?status=FLAKY&sort=impact');
  });

  it('changing the status filter updates the address, so it can be shared', async () => {
    mockApi({
      '/api/v1/projects/demo': demoProject,
      '/api/v1/projects/demo/tests': { items: [flakyCheckout, suspectedSearch], nextCursor: null },
    });
    openAt('/p/demo/tests');
    render(<App />);
    await screen.findByRole('link', { name: 'findsShoes' });

    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'BROKEN' } });
    expect(window.location.search).toBe('?status=BROKEN');
  });

  it('shows an encouraging empty state when there are no flaky tests', async () => {
    mockApi({
      '/api/v1/projects/demo': demoProject,
      '/api/v1/projects/demo/tests': { items: [], nextCursor: null },
    });
    openAt('/p/demo/tests?status=FLAKY');
    render(<App />);

    expect(await screen.findByText(/No tests match/)).toBeInTheDocument();
  });
});
