# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2026-11-21

First release: the M1 live demo.

### Added

- Report parser for JUnit XML from Surefire, Gradle, TestNG, Playwright, WebdriverIO and jest-junit, including retries, with DTDs rejected and size limits enforced.
- Evidence-based flaky detection (Stable, Flaky, Suspected, Failing, Broken, Not enough data) per environment, with impact and flip rate.
- Failure grouping by fingerprint.
- Upload API with safe repeats, a PostgreSQL job queue with leases and retries, and background processing.
- Read API and dashboard: overview, tests, test detail with evidence and timeline, runs with labelled failures, failure groups.
- GitHub Action uploader that never breaks the build, plus a `curl` and Jenkins guide.
- A demo project with deliberately flaky tests, and a go-live guide.
- Automatic deploys to Render: every merge to `main` is built, deployed, checked by commit SHA and smoke-tested. CI also starts the container image against PostgreSQL before it can be merged.

- Project documentation: constitution, product spec, implementation plan, task list, detection rules, data model, API contract, ADRs 0001–0009, workflow guide, templates, and the first feature spec (`specs/001-ci-feedback`).
- `scripts/check-docs.mjs`: requirement traceability and link checker.

[Unreleased]: https://github.com/Kuroh02/flakewatch/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Kuroh02/flakewatch/releases/tag/v0.1.0
