package io.github.chakrabartyaditi2002.flakewatch.projects;

import io.github.chakrabartyaditi2002.flakewatch.projects.internal.ProjectRepository;
import java.io.PrintStream;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Operator command to create a project and an ingest token before sign-in exists (FR-PRJ-007).
 *
 * <pre>
 * java -jar server.jar --flakewatch.bootstrap.project=demo --flakewatch.bootstrap.visibility=PUBLIC
 * </pre>
 *
 * It prints the token once to standard output (never to the logs) and exits.
 */
@Component
public class BootstrapRunner implements ApplicationRunner {

    private final ProjectRepository projects;
    private final TokenService tokens;
    private final Clock clock;
    private final ConfigurableApplicationContext context;
    private final String slug;
    private final String visibility;
    private final String defaultBranch;

    public BootstrapRunner(
            ProjectRepository projects,
            TokenService tokens,
            Clock clock,
            ConfigurableApplicationContext context,
            @Value("${flakewatch.bootstrap.project:}") String slug,
            @Value("${flakewatch.bootstrap.visibility:PUBLIC}") String visibility,
            @Value("${flakewatch.bootstrap.default-branch:main}") String defaultBranch) {
        this.projects = projects;
        this.tokens = tokens;
        this.clock = clock;
        this.context = context;
        this.slug = slug;
        this.visibility = visibility;
        this.defaultBranch = defaultBranch;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (slug == null || slug.isBlank()) {
            return;
        }
        Secret token = bootstrap(System.out);
        if (token != null) {
            System.exit(SpringApplication.exit(context, () -> 0));
        }
    }

    /** Creates the project if needed and issues a new ingest token. Returns the token. */
    Secret bootstrap(PrintStream out) {
        String vis = visibility.toUpperCase();
        if (!vis.equals("PUBLIC") && !vis.equals("PRIVATE")) {
            throw new IllegalArgumentException("flakewatch.bootstrap.visibility must be PUBLIC or PRIVATE");
        }
        long projectId = projects.findBySlug(slug)
                .map(ProjectRef::id)
                .orElseGet(() -> projects.create(slug, slug, vis, defaultBranch, clock.instant()));
        Secret token = tokens.issue(projectId, "bootstrap", TokenScope.INGEST);
        out.println("Project '" + slug + "' is ready. Its ingest token is shown only once:");
        out.println(token.value());
        return token;
    }
}
