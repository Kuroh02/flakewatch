package io.github.chakrabartyaditi2002.flakewatch.ingest.internal;

/** One uploaded report: its name, decompressed content, and SHA-256 of that content. */
public record ReceivedFile(String name, byte[] content, String sha256) {}
