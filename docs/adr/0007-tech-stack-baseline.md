# ADR-0007: Java 25, Spring Boot 4, Maven, plain SQL

**Status:** Accepted · **Date:** 2026-11-13

## Context

The stack should be current for 2026–27 software engineering roles, match the developer's strengths (Java, TypeScript, JUnit, TestNG, Selenium/Playwright, CI/CD, Docker), and suit the product's needs: bulk inserts, upserts, row locking, and streaming XML.

## Decision

| Area | Choice | Why |
|---|---|---|
| Language | Java 25 (LTS) | Newest LTS; supported by Spring Boot 4 (Java 17–25) |
| Framework | Spring Boot 4.x | Widely used in industry; Boot 4 is the current generation. Mind the modular starters: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, plus matching `-test` starters. |
| Build | Maven multi-module + wrapper | Familiar; Surefire's rerun output (`flakyFailure`) is used for dogfooding |
| Data access | Spring `JdbcClient` + hand-written SQL | We need `ON CONFLICT`, `RETURNING`, `FOR UPDATE SKIP LOCKED`, bulk operations and predictable queries. JPA would fight all of these. |
| Migrations | Flyway | Simple, forward-only SQL files |
| Tests | JUnit 6, AssertJ, Testcontainers, ArchUnit, PIT, RestAssured | See PLAN §18 |
| Frontend | React 19, TypeScript strict, Vite, TanStack Query, openapi-fetch | Mainstream, typed end to end from the contract |
| Action | TypeScript on `node24` | GitHub has deprecated the Node 20 runtime for actions |

## Spike S-5 result (PIT with JUnit 6)

*To be filled in during Phase 0.* Fallback: run `core`'s tests on JUnit Jupiter 5.14, which is possible because `core` has no Spring dependency.

## Consequences

- ➕ Current, mainstream and employable. Plain SQL shows real database skill.
- ➖ Spring Boot 4 is newer, so some libraries may lag behind. Spikes S-5 and S-6 check the risky ones early.
- ➖ Hand-written SQL means more code than JPA for simple reads. Accepted: queries are few and well tested.

## Alternatives considered

- **Kotlin:** nice, but Java matches the developer's background and most job descriptions.
- **Node.js backend:** the developer's backend experience is limited there, and the JVM's XML streaming and type system suit this problem well.
- **Gradle:** equally good. Maven chosen for familiarity and Surefire integration.
- **JPA/Hibernate, jOOQ:** JPA rejected for the reasons above. jOOQ is excellent but adds code generation and a learning curve for little gain at this size.
