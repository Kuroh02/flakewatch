import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';
import { PROJECT } from '../seed/scenario';
import { readSeed } from '../seed/global-setup';

/** What the global setup created (run and test ids). Read lazily: it exists only once setup has run. */
export const seed = () => readSeed();
export const project = (path = '') => `/p/${PROJECT}${path}`;

/** NFR-A11Y-001: no serious or critical axe issues (WCAG 2.2 AA rules) on the page as it is now. */
export async function expectAccessible(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze();
  const serious = results.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id} (${v.impact}): ${v.help}\n    ${v.nodes.map((n) => n.target.join(' ')).join('\n    ')}`);
  expect(serious, `axe found serious or critical issues on ${page.url()}`).toEqual([]);
}

/** The table row for a test, found by its visible name. */
export function rowFor(page: Page, text: string | RegExp) {
  return page.getByRole('row').filter({ has: page.getByRole('link', { name: text }) });
}
