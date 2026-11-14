import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import App from './App';

describe('T-0.10: Placeholder page showing /api/v1/version', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches and displays version information from /api/v1/version', async () => {
    const mockVersion = {
      version: '0.1.0-SNAPSHOT',
      commit: 'abc1234',
      builtAt: '2026-11-14T00:00:00Z',
    };

    (
      globalThis.fetch as unknown as ReturnType<typeof vi.fn>
    ).mockResolvedValueOnce({
      ok: true,
      json: async () => mockVersion,
    });

    render(<App />);

    expect(screen.getByText(/loading/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/0.1.0-SNAPSHOT/i)).toBeInTheDocument();
      expect(screen.getByText(/abc1234/i)).toBeInTheDocument();
    });

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/version');
  });

  it('displays an error message when the version fetch fails', async () => {
    (
      globalThis.fetch as unknown as ReturnType<typeof vi.fn>
    ).mockRejectedValueOnce(new Error('Failed to fetch'));

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/failed to load version/i)).toBeInTheDocument();
    });
  });
});
