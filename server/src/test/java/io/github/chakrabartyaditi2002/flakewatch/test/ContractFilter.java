package io.github.chakrabartyaditi2002.flakewatch.test;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds the RestAssured filter that checks every request and response against api/openapi.yaml. */
public final class ContractFilter {

    private ContractFilter() {}

    public static OpenApiValidationFilter create() {
        Path spec = Path.of("..", "api", "openapi.yaml");
        if (!Files.exists(spec)) {
            spec = Path.of("api", "openapi.yaml");
        }
        // Detail schemas are written as "summary + extra fields" (allOf). Without merging them
        // first, the validator checks each part on its own and treats the other part's fields
        // as unexpected, so a correct response would fail.
        OpenApiInteractionValidator validator = OpenApiInteractionValidator
                .createForSpecificationUrl(spec.toAbsolutePath().toUri().toString())
                .withResolveRefs(true)
                .withResolveCombinators(true)
                .build();
        return new OpenApiValidationFilter(validator);
    }
}
