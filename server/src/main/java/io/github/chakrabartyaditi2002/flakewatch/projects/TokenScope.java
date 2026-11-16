package io.github.chakrabartyaditi2002.flakewatch.projects;

/** What a project token may do (FR-PRJ-005). */
public enum TokenScope {
    INGEST("fw_ing_"),
    READ("fw_read_");

    private final String prefix;

    TokenScope(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
