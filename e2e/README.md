# End-to-end and accessibility tests

Playwright tests that use the real app the way a person would: in a browser, against the
`docker compose` stack, with data that arrived through the real upload API.

Each journey is named after the user story it proves (US-02, US-03, US-04, US-05, US-09). Every page
a journey visits is also scanned with [axe](https://github.com/dequelabs/axe-core); a serious or
critical accessibility issue fails the test (NFR-A11Y-001).

## What the seed data is

Before the tests run, `seed/global-setup.ts` uploads a small shop's UI test suite: four CI runs on
`main`, each on Chromium and Firefox, plus one real Surefire report. Each test's history is chosen
to produce one status (Flaky on Firefox only, Broken, a new failure, two kinds of timeout, and so
on). The table in [`seed/scenario.ts`](seed/scenario.ts) lists them. The setup waits until the
background worker has processed every upload and detection has labelled the tests.

## Run them locally

You need Docker and Node 24.

```bash
# 1. Start the app
cp .env.example .env
docker compose up --detach --build --wait

# 2. Create the projects. Copy the fw_ing_… token the first command prints.
docker compose run --rm app --flakewatch.bootstrap.project=e2e --flakewatch.bootstrap.visibility=PUBLIC
docker compose run --rm app --flakewatch.bootstrap.project=e2e-hidden --flakewatch.bootstrap.visibility=PRIVATE

# 3. Run the tests
cd e2e
npm install
npx playwright install chromium
FW_E2E_TOKEN=<the token> npm test
```

Seeding is safe to repeat: uploading the same data again changes nothing.

To start from a clean database, run `docker compose down --volumes` and repeat from step 1.

## Writing a new journey

- Name the test after the requirement it proves: `'US-04 #2: …'`.
- Find elements the way a user would: by role, label or visible text, not by CSS classes.
- Call `expectAccessible(page)` on every new page the journey opens.
- Tag a test `@phone` to also run it on a phone-sized screen.
- No retries and no fixed waits. Playwright waits for each expectation to become true, and the setup
  waits for the server's own status.
