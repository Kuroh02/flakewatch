package io.github.chakrabartyaditi2002.flakewatch.test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests requiring a PostgreSQL 17 database.
 * Reuses a single PostgreSQLContainer for the test run and truncates tables after each test.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(TruncateTablesExtension.class)
public abstract class AbstractPostgresIT {

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");
}
