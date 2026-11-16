// Entry point for the GitHub Action. It has no dependencies, so there is nothing to bundle.
import { run } from './src/main.js';

process.exitCode = await run({
  env: process.env,
  cwd: process.env.GITHUB_WORKSPACE || process.cwd(),
  log: (line) => console.log(line),
  now: () => Date.now(),
  sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
});
