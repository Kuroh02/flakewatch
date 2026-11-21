import { expect, test } from '@playwright/test';
import { expectAccessible, project } from './support';

test.describe('US-05: grouped failures', () => {
  test('US-05 #1: two timeouts that differ only in their numbers form one group', async ({ page }) => {
    await page.goto(project('/failures'));
    await expect(page.getByRole('heading', { level: 1, name: 'Failure groups' })).toBeVisible();

    // findsShoes timed out after 3012 ms in run 2 and after 2987 ms in run 3.
    const group = page
      .locator('tbody tr')
      .filter({ hasText: /Timeout after/ })
      .filter({ hasText: 'org.opentest4j.AssertionFailedError' });
    await expect(group).toHaveCount(1);
    await expect(group.locator('td[data-label="Times"]')).toHaveText('2');
    await expect(group.locator('td[data-label="Tests"]')).toHaveText('1');
    await expectAccessible(page);

    await group.getByRole('link').click();
    await expect(page.getByRole('heading', { name: 'Affected tests' })).toBeVisible();
    await expect(page.getByRole('link', { name: /SearchTest#findsShoes/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /SearchTest#suggestsTerms/ })).toHaveCount(0);
    await expectAccessible(page);
  });

  test('US-05 #2: the same message with a different exception type is a different group', async ({ page }) => {
    await page.goto(project('/failures'));

    const timeouts = page.locator('tbody tr').filter({ hasText: /Timeout after/ });
    await expect(timeouts).toHaveCount(2);
    const socket = timeouts.filter({ hasText: 'java.net.SocketTimeoutException' });
    await expect(socket).toHaveCount(1);

    await socket.getByRole('link').click();
    await expect(page.getByRole('link', { name: /SearchTest#suggestsTerms/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /SearchTest#findsShoes/ })).toHaveCount(0);
  });
});
