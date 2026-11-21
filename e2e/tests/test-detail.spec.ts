import { expect, test } from '@playwright/test';
import { expectAccessible, project, seed } from './support';

test.describe('US-04: one test\'s history and proof', () => {
  test('US-04 #2: flaky only on Firefox is shown per environment @phone', async ({ page }) => {
    await page.goto(project(`/tests/${seed().tests.paysWithCard}`));

    await expect(page.getByRole('heading', { level: 1, name: 'paysWithCard' })).toBeVisible();
    const environments = page.locator('section').filter({
      has: page.getByRole('heading', { name: 'By environment' }),
    });
    await expect(environments.getByRole('listitem').filter({ hasText: 'firefox' })).toContainText('Flaky');
    await expect(environments.getByRole('listitem').filter({ hasText: 'chromium' })).toContainText('Stable');
    await expectAccessible(page);
  });

  test('US-04 #1: the evidence links to the run that proves it, and the timeline shows the results', async ({ page }) => {
    await page.goto(project(`/tests/${seed().tests.paysWithCard}`));

    const evidence = page.locator('ul.evidence > li').first();
    await expect(evidence).toContainText('Passed on retry');
    await expect(evidence).toContainText('firefox');
    await expect(page.getByRole('heading', { name: /^Last \d+ results$/ })).toBeVisible();
    await expect(page.getByRole('table', { name: 'Results, oldest first' })).toBeAttached();

    await evidence.getByRole('link', { name: `run ${seed().runs[2]}` }).click();
    await expect(page).toHaveURL(new RegExp(`/runs/${seed().runs[2]}$`));
  });

  test('NFR-A11Y-001: a status is shown as words and a symbol, never by colour alone', async ({ page }) => {
    await page.goto(project(`/tests/${seed().tests.refundsCard}`));
    const badge = page.locator('.badge').first();
    await expect(badge).toContainText('Broken');
    await expect(badge.locator('.badge-icon')).toHaveText('✕');
    await expect(badge).toHaveAttribute('title', /last 3 default-branch runs/);
  });
});
