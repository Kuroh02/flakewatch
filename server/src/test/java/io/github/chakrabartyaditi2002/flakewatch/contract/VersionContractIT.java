package io.github.chakrabartyaditi2002.flakewatch.contract;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import io.github.chakrabartyaditi2002.flakewatch.FlakewatchApplication;
import io.github.chakrabartyaditi2002.flakewatch.test.ContractFilter;
import io.restassured.RestAssured;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest(
        classes = FlakewatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.flyway.enabled=false"})
@Import(VersionContractIT.StubDatabaseConfig.class)
class VersionContractIT {

    @LocalServerPort
    private int port;

    private OpenApiValidationFilter openApiValidationFilter;

    @TestConfiguration
    static class StubDatabaseConfig {
        @Bean
        @Primary
        DataSource dataSource() {
            return Mockito.mock(DataSource.class);
        }
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        openApiValidationFilter = ContractFilter.create();
    }

    @Test
    @DisplayName("NFR-QUAL-003: GET /api/v1/version conforms to OpenAPI contract")
    void versionEndpointConformsToContract() {
        given()
                .filter(openApiValidationFilter)
                .when()
                .get("/api/v1/version")
                .then()
                .statusCode(200)
                .body("version", notNullValue())
                .body("commit", notNullValue())
                .body("builtAt", notNullValue());
    }

    @Test
    @DisplayName("NFR-OPS-001: GET /api/v1/version reports the version the build was made from")
    void versionComesFromTheBuild() {
        // Maven (failsafe) passes in ${project.version}. A release must report its own version,
        // not a placeholder, so the live site shows which release is running.
        String expected = System.getProperty("flakewatch.expectedVersion");
        assertThat(expected).as("run through Maven: failsafe sets flakewatch.expectedVersion").isNotBlank();

        given()
                .when()
                .get("/api/v1/version")
                .then()
                .statusCode(200)
                .body("version", equalTo(expected));
    }
}
