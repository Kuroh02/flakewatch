package io.github.chakrabartyaditi2002.flakewatch.platform.meta;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class VersionController {

    private final BuildProperties buildProperties;
    private final Clock clock;

    public VersionController(@Autowired(required = false) BuildProperties buildProperties, Clock clock) {
        this.buildProperties = buildProperties;
        this.clock = clock;
    }

    @GetMapping("/version")
    public VersionResponse getVersion() {
        String version = (buildProperties != null && buildProperties.getVersion() != null)
                ? buildProperties.getVersion()
                : "dev";
        String commit = (buildProperties != null && buildProperties.get("commit") != null)
                ? buildProperties.get("commit")
                : System.getenv().getOrDefault("GIT_COMMIT", "dev");
        Instant builtAt = (buildProperties != null && buildProperties.getTime() != null)
                ? buildProperties.getTime()
                : clock.instant();
        return new VersionResponse(version, commit, builtAt);
    }
}
