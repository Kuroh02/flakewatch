package io.github.chakrabartyaditi2002.flakewatch.core.report;

/** A small problem that didn't stop the upload (FR-ING-009). {@code line} may be null. */
public record ParseWarning(String file, Integer line, String message) {}
