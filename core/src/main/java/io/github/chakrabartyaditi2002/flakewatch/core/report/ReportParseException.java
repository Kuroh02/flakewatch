package io.github.chakrabartyaditi2002.flakewatch.core.report;

/**
 * A problem that makes the whole upload fail, such as a file that isn't well-formed XML.
 * The message is written for the user: "file X, line Y: ...".
 */
public class ReportParseException extends RuntimeException {

    private final String file;
    private final Integer line;

    public ReportParseException(String file, Integer line, String reason) {
        super(describe(file, line, reason));
        this.file = file;
        this.line = line;
    }

    public String getFile() {
        return file;
    }

    public Integer getLine() {
        return line;
    }

    private static String describe(String file, Integer line, String reason) {
        return line == null ? "file " + file + ": " + reason : "file " + file + ", line " + line + ": " + reason;
    }
}
