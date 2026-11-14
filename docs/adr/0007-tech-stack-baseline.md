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

Executed in Phase 0 (Task T-0.18):
1. **Bytecode compatibility:** PIT 1.17.4 uses an internal ASM engine that does not yet recognize Java 25 bytecode (major version 69, failing with `Unsupported class file major version 69`). Compiling `core` with Java 21 bytecode compatibility (`<maven.compiler.release>21</maven.compiler.release>`) allows PIT to parse and mutate class files while running smoothly under the Java 25 JVM runtime.
2. **IPC socket communication:** PIT's minion process connects back to the Maven plugin process via a local TCP socket (`127.0.0.1`). In constrained sandbox environments, running the mutation coverage task with localhost socket permissions is required.
3. **JUnit Platform alignment:** By aligning JUnit Platform dependencies transitively via the parent POM and using `pitest-junit5-plugin:1.2.2`, PIT successfully discovers Jupiter test units, generates mutations, and kills 100% of mutations on the test suite (`TextHelperTest`).

## Consequences

- ➕ Current, mainstream and employable. Plain SQL shows real database skill.
- ➖ Spring Boot 4 is newer, so some libraries may lag behind. Spikes S-5 and S-6 check the risky ones early.
- ➖ Hand-written SQL means more code than JPA for simple reads. Accepted: queries are few and well tested.

## Alternatives considered

- **Kotlin:** nice, but Java matches the developer's background and most job descriptions.
- **Node.js backend:** the developer's backend experience is limited there, and the JVM's XML streaming and type system suit this problem well.
- **Gradle:** equally good. Maven chosen for familiarity and Surefire integration.
- **JPA/Hibernate, jOOQ:** JPA rejected for the reasons above. jOOQ is excellent but adds code generation and a learning curve for little gain at this size.
