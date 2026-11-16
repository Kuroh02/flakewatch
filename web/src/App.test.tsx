import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { mockApi, openAt } from './test/mockApi';
import { demoProject } from './test/sampleData';

describe('US-09: a visitor explores the demo without signing up', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('lists public projects on the home page, each linking to its overview', async () => {
    mockApi({ '/api/v1/projects': { items: [demoProject], nextCursor: null } });
    openAt('/');
    render(<App />);

    const link = await screen.findByRole('link', { name: /Shop demo/ });
    expect(link).toHaveAttribute('href', '/p/demo');
  });

  it('shows a friendly page for an address that does not exist', () => {
    mockApi({});
    openAt('/p/demo/settings');
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });

  it('says so clearly when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))));
    openAt('/');
    render(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not load/i);
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});
