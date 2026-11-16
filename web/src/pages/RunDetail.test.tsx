import '@testing-library/jest-dom/vitest';
import { render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { mockApi, openAt } from '../test/mockApi';
import { demoProject, nightlyRun } from '../test/sampleData';

describe('US-02: "Is this failure real?"', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('labels each failed test in a run as known flaky, new failure, broken or passed on retry', async () => {
    mockApi({
      '/api/v1/projects/demo': demoProject,
      '/api/v1/projects/demo/runs/7': {
        ...nightlyRun,
        repositoryUrl: 'https://github.com/aditi/shop',
        uploads: [{ uploadId: 1, jobKey: 'test', variant: '', status: 'PROCESSED' }],
        failures: [
          { testId: 42, testKey: 'com.shop.CheckoutTest#paysWithCard', label: 'KNOWN_FLAKY', status: 'FLAKY', failureGroupId: 5, message: 'element not clickable', variant: '', jobKey: 'test' },
          { testId: 44, testKey: 'com.shop.CartTest#addsItem', label: 'NEW_FAILURE', status: 'FAILING', failureGroupId: 6, message: 'expected 2 but was 1', variant: '', jobKey: 'test' },
          { testId: 45, testKey: 'com.shop.PaymentTest#refunds', label: 'BROKEN', status: 'BROKEN', failureGroupId: 7, message: 'HTTP 502', variant: '', jobKey: 'test' },
          { testId: 46, testKey: 'com.shop.LoginTest#logsIn', label: 'PASSED_ON_RETRY', status: 'FLAKY', failureGroupId: null, message: null, variant: '', jobKey: 'test' },
        ],
      },
    });
    openAt('/p/demo/runs/7');
    render(<App />);

    const row = (await screen.findByText('com.shop.CartTest#addsItem')).closest('tr');
    expect(row).not.toBeNull();
    expect(within(row as HTMLElement).getByText('New failure')).toBeInTheDocument();
    expect(screen.getByText('Known flaky')).toBeInTheDocument();
    expect(screen.getByText('Broken')).toBeInTheDocument();
    expect(screen.getByText('Passed on retry')).toBeInTheDocument();
  });
});
