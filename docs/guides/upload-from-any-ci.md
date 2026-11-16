# Upload results from any CI

**Implements:** FR-CI-006

On GitHub Actions, use the [upload action](../../action/README.md). Everywhere else (Jenkins, GitLab, a laptop), one `curl` command is enough.

## What you need

- Your Flakewatch address, for example `https://flakewatch.example.com`.
- The project's **ingest token** (it starts with `fw_ing_`). Keep it in your CI's secret store, never in the repository.
- The JUnit XML files your tests wrote, for example `target/surefire-reports/TEST-*.xml`.

## The command

```bash
curl --fail-with-body --retry 3 --retry-all-errors --max-time 120 \
  -H "Authorization: Bearer $FLAKEWATCH_TOKEN" \
  -F 'metadata={"commitSha":"'"$GIT_COMMIT"'","branch":"'"$GIT_BRANCH"'","provider":"OTHER","runId":"'"$BUILD_ID"'","jobKey":"unit-tests"};type=application/json' \
  -F "files=@target/surefire-reports/TEST-com.shop.CartTest.xml" \
  -F "files=@target/surefire-reports/TEST-com.shop.CheckoutTest.xml" \
  "$FLAKEWATCH_URL/api/v1/uploads"
```

Two details matter:

- The `metadata` part must end with `;type=application/json`. Without it the server answers `400`.
- Add one `-F "files=@..."` per report file, up to 20 files per upload. Files can be gzip-compressed.

A successful upload answers `202 Accepted` with a link to the run:

```json
{ "uploadId": 42, "runId": 7, "status": "RECEIVED", "statusUrl": "/api/v1/uploads/42",
  "runUrl": "https://flakewatch.example.com/p/shop/runs/7", "duplicate": false }
```

Sending the same upload again (for example when the CI retries the step) is safe: you get the original back with `200` and `"duplicate": true`.

## The metadata fields

| Field | Required | What to put there |
|---|---|---|
| `commitSha` | yes | The full commit hash that was tested (lowercase hex) |
| `branch` | yes | The branch name, for example `main` |
| `runId` | yes | Your CI's build number or ID |
| `provider` | no | `JENKINS`, `GITLAB`, `GITHUB` or `OTHER` (default) |
| `runAttempt` | no | Increase it when the same build is re-run (default 1) |
| `jobKey` | no | Unique per job or shard in one build (default `default`) |
| `variant` | no | Environment label, such as `firefox` or `java-21` |
| `runStartedAt` | no | When the build started, like `2026-11-16T10:00:00Z` |

Shards of one build share the same `runId` and use different `jobKey` values.

## Jenkins

This `Jenkinsfile` runs the tests, then uploads the reports even when tests failed. The upload never fails the build: `|| true` keeps Flakewatch problems out of your build result.

```groovy
pipeline {
  agent any
  environment {
    FLAKEWATCH_URL   = 'https://flakewatch.example.com'
    FLAKEWATCH_TOKEN = credentials('flakewatch-ingest-token')
  }
  stages {
    stage('Test') {
      steps {
        sh './mvnw -B verify -Dsurefire.rerunFailingTestsCount=1'
      }
    }
  }
  post {
    always {
      junit 'target/surefire-reports/*.xml'
      sh '''
        set +x   # don't echo the token
        FILES=""
        for f in target/surefire-reports/TEST-*.xml; do FILES="$FILES -F files=@$f"; done
        curl --fail-with-body --retry 3 --retry-all-errors --max-time 120 \
          -H "Authorization: Bearer $FLAKEWATCH_TOKEN" \
          -F "metadata={\\"commitSha\\":\\"$GIT_COMMIT\\",\\"branch\\":\\"$GIT_BRANCH\\",\\"provider\\":\\"JENKINS\\",\\"runId\\":\\"$BUILD_NUMBER\\",\\"jobKey\\":\\"$STAGE_NAME\\"};type=application/json" \
          $FILES "$FLAKEWATCH_URL/api/v1/uploads" || echo "Flakewatch upload failed; the build result is unchanged."
      '''
    }
  }
}
```

On Jenkins, `GIT_BRANCH` is often `origin/main`; strip the `origin/` prefix so runs match your default branch.

If a project writes more than 20 report files, send them in groups of 20 with job keys like `unit-1`, `unit-2`.

## Checking the result

```bash
curl -H "Authorization: Bearer $FLAKEWATCH_TOKEN" "$FLAKEWATCH_URL/api/v1/uploads/42"
```

The status moves from `RECEIVED` to `PROCESSED` (usually within seconds) and then shows the counts. If the files couldn't be read, it says `FAILED` with the file and line.
