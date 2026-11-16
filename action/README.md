# Flakewatch upload action

Sends your JUnit XML test results to Flakewatch after your tests run. Flakewatch then tells you which
failures are **flaky** (they pass and fail on the same code) and which are **real**.

## Use it

Add one step after your tests:

```yaml
- name: Upload test results to Flakewatch
  if: ${{ !cancelled() }}          # run even when tests failed: that's the point
  uses: <owner>/flakewatch/action@action-v1
  with:
    server-url: https://flakewatch.example.com
    token: ${{ secrets.FLAKEWATCH_INGEST_TOKEN }}
    files: '**/target/surefire-reports/TEST-*.xml'
```

For a matrix (for example one job per browser), give each job its own key and a variant:

```yaml
    job-key: e2e-${{ matrix.browser }}
    variant: ${{ matrix.browser }}
```

## What it does

1. Finds the report files (`node_modules` and `.git` are never searched).
2. Compresses them and uploads them, up to 20 files per request.
3. If the server is asleep or busy, it retries after 2, 8 and 30 seconds, within `timeout-seconds`.
4. Writes a short summary with the counts and a link to the run.

## It never breaks your build

If Flakewatch is down, slow or misconfigured, the step prints a warning and your job's result is
unchanged. Set `fail-on-error: true` only if you want upload problems to fail the step.

On pull requests from forks, GitHub doesn't share secrets, so the token is empty. The step then
skips with a notice.

## Inputs

| Input | Default | What it's for |
|---|---|---|
| `server-url` | (required) | Your Flakewatch address |
| `token` | (required) | The project's ingest token |
| `files` | `**/TEST-*.xml` | Glob pattern(s), one per line |
| `job-key` | the job's name | Must be unique per job and shard |
| `variant` | empty | Environment label, such as `firefox` |
| `fail-on-error` | `false` | Fail the step when the upload fails |
| `timeout-seconds` | `120` | Total time budget, including a server waking up |

## Develop

The action has no dependencies. Tests use Node's built-in test runner:

```bash
npm --prefix action test
```
