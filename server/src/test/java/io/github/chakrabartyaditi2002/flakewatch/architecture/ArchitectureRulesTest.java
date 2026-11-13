package io.github.chakrabartyaditi2002.flakewatch.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@AnalyzeClasses(
    packages = "io.github.chakrabartyaditi2002.flakewatch",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
@DisplayName("NFR-QUAL-002: Architecture rules")
public class ArchitectureRulesTest {

    private static final ArchRule CORE_INDEPENDENCE_RULE =
        noClasses()
            .that().resideInAPackage("..flakewatch.core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..flakewatch.server..",
                "org.springframework..",
                "java.sql..",
                "javax.sql.."
            )
            .because("core is pure domain logic independent of Spring, database or server layers (PLAN §6.3 rule 1)");

    private static final ArchRule CLOCK_BAN_RULE =
        noClasses()
            .that().resideOutsideOfPackage("..platform.time..")
            .should().callMethodWhere(DescribedPredicate.describe(
                "call to Clock.system* or Instant/Date.now",
                (JavaCall<?> call) -> (call.getTargetOwner().isAssignableTo(Instant.class) && call.getName().equals("now"))
                     || (call.getTargetOwner().isAssignableTo(LocalDate.class) && call.getName().equals("now"))
                     || (call.getTargetOwner().isAssignableTo(LocalDateTime.class) && call.getName().equals("now"))
                     || (call.getTargetOwner().isAssignableTo(LocalTime.class) && call.getName().equals("now"))
                     || (call.getTargetOwner().isAssignableTo(OffsetDateTime.class) && call.getName().equals("now"))
                     || (call.getTargetOwner().isAssignableTo(OffsetTime.class) && call.getName().equals("now"))
                     || (call.getTargetOwner().isAssignableTo(ZonedDateTime.class) && call.getName().equals("now"))
                     || (call.getTargetOwner().isAssignableTo(Clock.class) && call.getName().startsWith("system"))
            ))
            .because("Time must be controlled through injected java.time.Clock for determinism (PLAN §6.3 rule 4)");

    private static final ArchRule RANDOMNESS_BAN_RULE =
        noClasses()
            .that().resideOutsideOfPackage("..platform.random..")
            .should().callConstructorWhere(DescribedPredicate.describe(
                "call to new Random()",
                (JavaCall<?> call) -> call.getTargetOwner().isAssignableTo(Random.class)
            ))
            .orShould().callMethodWhere(DescribedPredicate.describe(
                "call to Math.random, UUID.randomUUID, or ThreadLocalRandom.current",
                (JavaCall<?> call) -> (call.getTargetOwner().isAssignableTo(Math.class) && call.getName().equals("random"))
                     || (call.getTargetOwner().isAssignableTo(UUID.class) && call.getName().equals("randomUUID"))
                     || (call.getTargetOwner().getName().equals("java.util.concurrent.ThreadLocalRandom") && call.getName().equals("current"))
            ))
            .because("Randomness must be obtained from injected RandomSource for determinism (PLAN §6.3 rule 4)");

    private static final ArchRule NO_FIELD_INJECTION_RULE =
        noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .orShould().beAnnotatedWith("jakarta.inject.Inject")
            .orShould().beAnnotatedWith("jakarta.annotation.Resource")
            .because("Field injection is forbidden; use constructor injection (PLAN §6.3 rule 6)");

    @ArchTest
    @DisplayName("NFR-QUAL-002: core module must not depend on server, Spring, or SQL")
    void coreIndependence(JavaClasses classes) {
        CORE_INDEPENDENCE_RULE.check(classes);
    }

    @ArchTest
    @DisplayName("NFR-QUAL-002: only platform.time may call Clock.system* or Instant/Date.now")
    void clockBan(JavaClasses classes) {
        CLOCK_BAN_RULE.check(classes);
    }

    @ArchTest
    @DisplayName("NFR-QUAL-002: only platform.random may invoke Random, Math.random, UUID.randomUUID, or ThreadLocalRandom")
    void randomnessBan(JavaClasses classes) {
        RANDOMNESS_BAN_RULE.check(classes);
    }

    @ArchTest
    @DisplayName("NFR-QUAL-002: field injection is forbidden; constructor injection only")
    void noFieldInjection(JavaClasses classes) {
        NO_FIELD_INJECTION_RULE.check(classes);
    }

    @Test
    @DisplayName("NFR-QUAL-002: verification that clock ban detects unauthorized Instant.now() calls")
    void verifiesClockBanFailsOnViolation() {
        JavaClasses violating = new ClassFileImporter().importClasses(ViolatingClockUsage.class);
        EvaluationResult result = CLOCK_BAN_RULE.evaluate(violating);
        assertThat(result.hasViolation()).isTrue();
    }

    @Test
    @DisplayName("NFR-QUAL-002: verification that randomness ban detects unauthorized UUID.randomUUID() calls")
    void verifiesRandomnessBanFailsOnViolation() {
        JavaClasses violating = new ClassFileImporter().importClasses(ViolatingRandomUsage.class);
        EvaluationResult result = RANDOMNESS_BAN_RULE.evaluate(violating);
        assertThat(result.hasViolation()).isTrue();
    }

    @Test
    @DisplayName("NFR-QUAL-002: verification that no-field-injection detects @Autowired fields")
    void verifiesNoFieldInjectionFailsOnViolation() {
        JavaClasses violating = new ClassFileImporter().importClasses(ViolatingFieldInjectionUsage.class);
        EvaluationResult result = NO_FIELD_INJECTION_RULE.evaluate(violating);
        assertThat(result.hasViolation()).isTrue();
    }

    static class ViolatingClockUsage {
        void run() {
            Instant.now();
        }
    }

    static class ViolatingRandomUsage {
        void run() {
            UUID.randomUUID();
        }
    }

    static class ViolatingFieldInjectionUsage {
        @Autowired
        private String forbiddenField;
    }
}
