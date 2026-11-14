package io.github.chakrabartyaditi2002.flakewatch.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import io.github.chakrabartyaditi2002.flakewatch.FlakewatchApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

@SpringBootTest(classes = FlakewatchApplication.class, properties = {"spring.flyway.enabled=false"})
@Import(NoDbOnLivenessIT.StubDatabaseConfig.class)
@TestExecutionListeners(
        listeners = {DependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
class NoDbOnLivenessIT {

    @Autowired
    private HealthEndpoint healthEndpoint;

    static volatile boolean databaseUp = true;

    @TestConfiguration
    static class StubDatabaseConfig {

        @Bean
        @Primary
        DataSource dataSource() {
            return new StubDataSource();
        }
    }

    static class StubDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            if (!databaseUp) {
                throw new SQLException("Database connection refused (simulated outage)");
            }
            DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
                    DatabaseMetaData.class.getClassLoader(),
                    new Class<?>[] {DatabaseMetaData.class},
                    (proxy, method, args) -> {
                        if ("getDatabaseProductName".equals(method.getName())) {
                            return "PostgreSQL";
                        }
                        return null;
                    });

            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if ("isValid".equals(method.getName())) {
                            return true;
                        }
                        if ("getMetaData".equals(method.getName())) {
                            return metaData;
                        }
                        if ("close".equals(method.getName())) {
                            return null;
                        }
                        return null;
                    });
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {}

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    @Test
    @DisplayName("NFR-OBS-003: The liveness check never touches the database; the readiness check does")
    void livenessReturnsUpWhileDatabaseIsDown() {
        // Step 1: When DB is UP, both liveness and readiness are UP
        databaseUp = true;
        HealthDescriptor livenessUp = healthEndpoint.healthForPath("liveness");
        assertThat(livenessUp.getStatus()).isEqualTo(Status.UP);

        HealthDescriptor readinessUp = healthEndpoint.healthForPath("readiness");
        assertThat(readinessUp.getStatus()).isEqualTo(Status.UP);

        // Step 2: Simulate database outage (pause/down)
        databaseUp = false;

        // Liveness check NEVER touches the database -> remains UP
        HealthDescriptor livenessDuringOutage = healthEndpoint.healthForPath("liveness");
        assertThat(livenessDuringOutage.getStatus()).isEqualTo(Status.UP);

        // Readiness check DOES touch the database -> reports DOWN
        HealthDescriptor readinessDuringOutage = healthEndpoint.healthForPath("readiness");
        assertThat(readinessDuringOutage.getStatus()).isEqualTo(Status.DOWN);

        // Step 3: Database recovers -> readiness returns to UP
        databaseUp = true;
        HealthDescriptor readinessRecovered = healthEndpoint.healthForPath("readiness");
        assertThat(readinessRecovered.getStatus()).isEqualTo(Status.UP);
    }
}
