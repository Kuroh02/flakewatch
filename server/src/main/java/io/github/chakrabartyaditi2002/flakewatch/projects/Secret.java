package io.github.chakrabartyaditi2002.flakewatch.projects;

/** Holds a token value. {@code toString()} never shows it, so it can't leak into logs (PLAN §6.3 rule 5). */
public record Secret(String value) {

    @Override
    public String toString() {
        return "Secret[<redacted>]";
    }
}
