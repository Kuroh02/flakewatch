package io.github.chakrabartyaditi2002.flakewatch.platform.meta;

import java.time.Instant;

public record VersionResponse(String version, String commit, Instant builtAt) {}
