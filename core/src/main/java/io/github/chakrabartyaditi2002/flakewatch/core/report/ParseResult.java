package io.github.chakrabartyaditi2002.flakewatch.core.report;

import java.util.List;

/** Everything parsed from one upload's files. */
public record ParseResult(List<ParsedTestCase> testCases, List<ParseWarning> warnings) {

    public ParseResult {
        testCases = List.copyOf(testCases);
        warnings = List.copyOf(warnings);
    }
}
