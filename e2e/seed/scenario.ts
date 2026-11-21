import { Surefire } from './surefire';

/**
 * The seed data: a small shop's UI test suite, run 4 times on `main`, on Chromium and Firefox.
 * Each test's history is chosen to produce one status the journeys check:
 *
 * | Test                          | History                                           | Expected status            |
 * |-------------------------------|---------------------------------------------------|----------------------------|
 * | CheckoutTest.paysWithCard     | Firefox: passed on retry in run 2, failed in run 4 | Flaky on firefox only      |
 * | SearchTest.findsShoes         | Chromium: timeouts, passed on retry, runs 2 and 3 | Flaky; one failure group   |
 * | SearchTest.suggestsTerms      | Chromium: socket timeout in run 4                 | Failing; a separate group  |
 * | CartTest.addsItem             | Passed runs 1-3, failed in run 4                  | Failing ("new failure")    |
 * | RefundTest.refundsCard        | Failed every run                                  | Broken                     |
 * | CatalogTest.showsProduct x 55 | Passed once (run 1)                               | Not enough data (paging)   |
 * | SecurityTest.<img …>          | Always passes; its name is an XSS probe           | Stable, shown as plain text |
 */
export const PROJECT = 'e2e';
export const PRIVATE_PROJECT = 'e2e-hidden';
export const RUNS = [1, 2, 3, 4] as const;
export const BROWSERS = ['chromium', 'firefox'] as const;
export const XSS_NAME = '<img src=x onerror=alert(1)>';

export type Run = (typeof RUNS)[number];
export type Browser = (typeof BROWSERS)[number];

export function commitOf(run: Run): string {
  return `c0ffee${run}${run}${run}`;
}

/** The report files one CI job (one browser) produced in one run. */
export function reports(run: Run, browser: Browser): Surefire[] {
  const chromium = browser === 'chromium';
  const firefox = browser === 'firefox';

  const checkout = Surefire.suite('e2e.shop.CheckoutTest');
  if (firefox && run === 2) checkout.flaky('paysWithCard', 'Payment iframe not ready after 5000 ms');
  else if (firefox && run === 4) checkout.failedTwice('paysWithCard', 'Payment iframe not ready after 5000 ms');
  else checkout.passed('paysWithCard');

  const search = Surefire.suite('e2e.shop.SearchTest');
  if (chromium && run === 2) search.flaky('findsShoes', 'Timeout after 3012 ms');
  else if (chromium && run === 3) search.flaky('findsShoes', 'Timeout after 2987 ms');
  else search.passed('findsShoes');
  if (chromium && run === 4) search.failedTwice('suggestsTerms', 'Timeout after 3100 ms', 'java.net.SocketTimeoutException');
  else search.passed('suggestsTerms');

  const cart = Surefire.suite('e2e.shop.CartTest');
  if (run === 4) cart.failedTwice('addsItem', 'expected 1 item in the cart but was 0');
  else cart.passed('addsItem');
  cart.passed('removesItem');

  const refund = Surefire.suite('e2e.shop.RefundTest').failedTwice('refundsCard', 'HTTP 502 from payments-stub');

  const security = Surefire.suite('e2e.shop.SecurityTest').passed(XSS_NAME);

  const files = [checkout, search, cart, refund, security];
  if (chromium && run === 1) {
    const catalog = Surefire.suite('e2e.shop.CatalogTest');
    for (let i = 1; i <= 55; i++) catalog.passed(`showsProduct(int)[${i}]`);
    files.push(catalog);
  }
  return files;
}
