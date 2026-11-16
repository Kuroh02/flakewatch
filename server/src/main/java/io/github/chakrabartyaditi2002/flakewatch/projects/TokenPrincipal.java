package io.github.chakrabartyaditi2002.flakewatch.projects;

/** A machine caller authenticated by a project token. */
public record TokenPrincipal(long tokenId, long projectId, TokenScope scope) {}
