import { expect, test } from '@playwright/test';
import { PRIVATE_PROJECT, PROJECT } from '../seed/scenario';
import { expectAccessible, project, seed } from './support';

test.describe('US-09: explore a public project without signing in', () => {
  test('US-09 #1: every read-only page opens from the home page, with no accessibility issues @phone', async ({ page }) => {
    await page.goto('/');
    await expectAccessible(page);

    await page.getByRole('link', { name: PROJECT, exact: true }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Overview' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Recent runs' })).toBeVisible();
    await expectAccessible(page);

    const sections = page.getByRole('navigation', { name: 'Project sections' });
    for (const [tab, heading] of [
      ['Tests', 'Tests'],
      ['Runs', 'Runs'],
      ['Failure groups', 'Failure groups'],
    ] as const) {
      await sections.getByRole('link', { name: tab }).click();
      await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
      await expect(sections.getByRole('link', { name: tab })).toHaveAttribute('aria-current', 'page');
      await expectAccessible(page);
    }
  });

  test('US-09 #1: a real Surefire report shows up as a run', async ({ page }) => {
    await page.goto(project(`/runs/${seed().realRun}`));
    await expect(page.getByRole('link', { name: /sample\.SampleTest#testFailing/ }).first()).toBeVisible();
    await expect(page.getByRole('link', { name: /sample\.SampleTest#testFlaky/ })).toBeVisible();
  });

  test('US-09 #2: a private project looks exactly like one that does not exist', async ({ page }) => {
    await page.goto(`/p/${PRIVATE_PROJECT}`);
    await expect(page.getByRole('heading', { level: 1, name: 'Page not found' })).toBeVisible();

    await page.goto('/p/no-such-project');
    await expect(page.getByRole('heading', { level: 1, name: 'Page not found' })).toBeVisible();
  });
});
