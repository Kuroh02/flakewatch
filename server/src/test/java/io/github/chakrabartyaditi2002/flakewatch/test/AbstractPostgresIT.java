package io.github.chakrabartyaditi2002.flakewatch.test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests requiring a PostgreSQL 17 database.
 *
 * <p>One container serves the whole test run. It is started once, here, and never stopped by a
 * test class: Spring caches the application context between classes, and that cached context
 * keeps pointing at this container. (Declaring it as a per-class {@code @Container} stopped it
 * after the first class and left every later class talking to a dead port.) Testcontainers'
 * Ryuk sidecar removes it when the JVM exits. Tables are truncated after each test.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(TruncateTablesExtension.class)
public abstract class AbstractPostgresIT {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
