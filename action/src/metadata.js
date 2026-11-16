/**
 * Builds the upload metadata from the GitHub Actions environment (FR-CI-001, PLAN §14).
 *
 * - Commit: GITHUB_SHA. On pull requests this is GitHub's temporary merge commit: that is the
 *   code that actually ran, and it stays the same when a run is re-run.
 * - Branch: the PR's own branch (GITHUB_HEAD_REF) for pull requests, otherwise GITHUB_REF_NAME.
 */
export function collectMetadata({ env, event, inputs }) {
  const attempt = Number.parseInt(env.GITHUB_RUN_ATTEMPT ?? '', 10);
  const prNumber = event?.pull_request?.number;
  const jobKey = (inputs['job-key'] ?? '').trim() || env.GITHUB_JOB || 'default';
  return {
    commitSha: (env.GITHUB_SHA ?? '').toLowerCase(),
    branch: env.GITHUB_HEAD_REF || env.GITHUB_REF_NAME || '',
    provider: 'GITHUB',
    runId: env.GITHUB_RUN_ID ?? '',
    runAttempt: Number.isInteger(attempt) && attempt >= 1 ? attempt : 1,
    jobKey,
    variant: (inputs.variant ?? '').trim(),
    pullRequest: Number.isInteger(prNumber) && prNumber >= 1 ? prNumber : null,
    repositoryUrl:
      env.GITHUB_SERVER_URL && env.GITHUB_REPOSITORY ? `${env.GITHUB_SERVER_URL}/${env.GITHUB_REPOSITORY}` : null,
  };
}
