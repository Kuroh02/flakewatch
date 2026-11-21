import { expect, test } from '@playwright/test';
import { expectAccessible, project, rowFor, seed } from './support';

test.describe('US-02: is this failure real?', () => {
  test('US-02 #1: each failure in a red run is labelled known flaky, new or broken @phone', async ({ page }) => {
    await page.goto(project(`/runs/${seed().runs[4]}`));

    await expect(page.getByRole('heading', { name: 'Failures' })).toBeVisible();
    await expect(rowFor(page, /CheckoutTest#paysWithCard/)).toContainText('Known flaky');
    await expect(rowFor(page, /CartTest#addsItem/).first()).toContainText('New failure');
    await expect(rowFor(page, /RefundTest#refundsCard/).first()).toContainText('Broken');
    // Only the failures nobody knew about count as "worth a look".
    await expect(page.getByText(/new failures?: worth a look/)).toBeVisible();
    await expectAccessible(page);
  });

  test('US-02 #1: a failure links to that test\'s history', async ({ page }) => {
    await page.goto(project(`/runs/${seed().runs[4]}`));
    await page.getByRole('link', { name: /RefundTest#refundsCard/ }).first().click();

    await expect(page).toHaveURL(new RegExp(`/tests/${seed().tests.refundsCard}$`));
    await expect(page.getByRole('heading', { level: 1, name: 'refundsCard' })).toBeVisible();
  });

  test('US-02 #2: a test that passed on retry is shown as such, and is now Flaky', async ({ page }) => {
    await page.goto(project(`/runs/${seed().runs[2]}`));

    const row = rowFor(page, /CheckoutTest#paysWithCard/);
    await expect(row).toContainText('Passed on retry');
    await expect(row).toContainText('firefox');

    await row.getByRole('link').click();
    await expect(page.getByRole('heading', { level: 1, name: 'paysWithCard' })).toBeVisible();
    await expect(page.locator('.badge').first()).toContainText('Flaky');
  });
});
