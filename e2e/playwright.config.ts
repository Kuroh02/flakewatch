import { defineConfig, devices } from '@playwright/test';

/**
 * Runs against the real app: `docker compose up` locally, the same stack in CI.
 * See e2e/README.md for how to start it and get the token the seed step needs.
 */
export default defineConfig({
  testDir: './tests',
  globalSetup: './seed/global-setup.ts',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  // No retries: a retry would hide exactly the kind of flakiness this project is about. A flaky
  // journey shows up in our own Flakewatch project instead (the CI job uploads these results).
  retries: 0,
  reporter: [['list'], ['junit', { outputFile: 'test-results/junit.xml' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'phone', use: { ...devices['Pixel 7'] }, grep: /@phone/ },
  ],
});
