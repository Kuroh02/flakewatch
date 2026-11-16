# Flakewatch demo project

A tiny "shop" test suite that feeds the Flakewatch live demo with real CI results. Copy this folder
into its own public repository (for example `flakewatch-demo`).

| Test | Behaviour | Flakewatch should show |
|---|---|---|
| `CheckoutTest.paysWithCard` | A slow dependency times out about 1 call in 6 | Flaky (passed on retry) |
| `CheckoutTest.sendsConfirmationEmail` | A race with a background thread | Flaky |
| `SearchTest.ranksBestSellersFirst` | Unreliable only on Java 25 | Flaky on `java-25`, Stable on `java-21` |
| `RefundTest.refundsCard` | Always fails | Broken |
| Everything else | Always passes | Stable |

## Set it up

1. Create a public repository and copy this folder's contents into it.
2. In the repository settings, add:
   - a variable `FLAKEWATCH_URL` with your Flakewatch address;
   - a secret `FLAKEWATCH_INGEST_TOKEN` with the demo project's ingest token.
3. Push. The tests run on every push and every 3 hours, on Java 21 and 25.

The build keeps going even though `RefundTest` is broken (`testFailureIgnore`), so the reports
are always written and uploaded.
