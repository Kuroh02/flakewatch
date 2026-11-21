import { expect, test } from '@playwright/test';
import { XSS_NAME } from '../seed/scenario';
import { expectAccessible, project } from './support';

test.describe('US-03: ranked list of flaky tests', () => {
  test('US-03 #1: Flaky tests are sorted by impact, worst first, with impact, flip rate and last failure', async ({ page }) => {
    await page.goto(project('/tests'));
    await page.getByLabel('Status').selectOption({ label: 'Flaky' });

    await expect(page).toHaveURL(/status=FLAKY/);
    const rows = page.locator('tbody tr');
    await expect(rows.filter({ hasText: 'paysWithCard' })).toHaveCount(1);
    await expect(rows.filter({ hasText: 'findsShoes' })).toHaveCount(1);
    await expect(rows.filter({ hasNotText: 'Flaky' })).toHaveCount(0);

    const impacts = (await page.locator('tbody td[data-label="Impact"]').allInnerTexts()).map(Number);
    expect(impacts).toEqual([...impacts].sort((a, b) => b - a));
    await expect(rows.first().locator('td[data-label="Flip rate"]')).toHaveText(/%/);
    await expect(rows.first().locator('td[data-label="Last failure"]')).not.toHaveText('—');
    await expectAccessible(page);
  });

  test('US-03 #3 / FR-UI-007: more than 50 tests page, and the URL restores the exact view', async ({ page }) => {
    await page.goto(project('/tests?sort=name'));
    const rows = page.locator('tbody tr');
    await expect(rows).toHaveCount(50);
    const firstOnPageOne = (await rows.first().textContent()) ?? '';

    await page.getByRole('link', { name: 'Next page →' }).click();
    await expect(page).toHaveURL(/cursor=/);
    // Wait for page 2's rows to replace page 1's before reading them.
    await expect(rows.first()).not.toHaveText(firstOnPageOne);
    const secondPage = await rows.allTextContents();
    expect(secondPage.length).toBeGreaterThan(0);
    expect(secondPage.length).toBeLessThan(50);

    // Share the link: a fresh page with the same URL shows the same tests and the same sort.
    const shared = await page.context().newPage();
    await shared.goto(page.url());
    await expect(shared.getByLabel('Sort by')).toHaveValue('name');
    await expect(shared.locator('tbody tr')).toHaveText(secondPage);

    await shared.getByRole('link', { name: '← First page' }).click();
    await expect(shared.locator('tbody tr')).toHaveCount(50);
  });

  test('FR-UI-007: a filter with no matches explains itself instead of showing an empty table', async ({ page }) => {
    await page.goto(project('/tests?status=SUSPECTED'));
    await expect(page.getByText('No tests match "suspected".')).toBeVisible();
  });

  test('NFR-SEC-006: a test named like HTML is shown as text, never run as HTML', async ({ page }) => {
    let dialogs = 0;
    page.on('dialog', async (dialog) => {
      dialogs++;
      await dialog.dismiss();
    });
    await page.goto(project('/tests?q=onerror'));

    await expect(page.getByRole('link', { name: XSS_NAME })).toBeVisible();
    await expect(page.locator('main img')).toHaveCount(0);
    expect(dialogs).toBe(0);
  });
});
