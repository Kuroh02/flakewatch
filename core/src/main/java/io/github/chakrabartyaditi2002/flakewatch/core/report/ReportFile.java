package io.github.chakrabartyaditi2002.flakewatch.core.report;

/** One uploaded report, already decompressed. */
public record ReportFile(String name, byte[] content) {}
