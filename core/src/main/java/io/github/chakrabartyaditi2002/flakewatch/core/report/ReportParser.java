package io.github.chakrabartyaditi2002.flakewatch.core.report;

import io.github.chakrabartyaditi2002.flakewatch.core.testkey.TestKeys;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Streaming, hardened parser for JUnit XML reports (FR-ING-006, FR-ING-007, FR-ING-009, FR-ING-013).
 *
 * <p>Rules are in PLAN §7.3. DTDs are rejected outright, so XXE and entity-expansion attacks can't
 * work (NFR-SEC-002). Files are read in name order; a test that appears more than once gets its
 * executions appended in order (that's how Gradle's test-retry plugin reports retries).
 */
public final class ReportParser {

    /** Prefix a quarantine library writes into a skipped entry that really failed (FR-ING-013). */
    public static final String QUARANTINE_MARKER = "[flakewatch:quarantined-failure]";

    private static final int MAX_WARNINGS = 100;

    private final ParserLimits limits;
    private final XMLInputFactory factory;

    public ReportParser(ParserLimits limits) {
        this.limits = limits;
        this.factory = hardenedFactory();
    }

    public ReportParser() {
        this(ParserLimits.defaults());
    }

    static XMLInputFactory hardenedFactory() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        f.setProperty(XMLInputFactory.IS_COALESCING, false);
        return f;
    }

    /**
     * Reads only up to the first element. True when it is {@code <testsuites>} or {@code <testsuite>}
     * (FR-ING-008). Anything that isn't XML, or starts with a DTD, is not a report.
     */
    public static boolean isJunitReport(byte[] content) {
        try {
            XMLStreamReader r = hardenedFactory().createXMLStreamReader(reader(content));
            try {
                while (r.hasNext()) {
                    int event = r.next();
                    if (event == XMLStreamConstants.DTD) {
                        return false;
                    }
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String root = r.getLocalName();
                        return root.equals("testsuites") || root.equals("testsuite");
                    }
                }
                return false;
            } finally {
                r.close();
            }
        } catch (XMLStreamException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Decodes the bytes ourselves: the encoding comes from a byte-order mark or the XML declaration
     * (UTF-8 by default), and invalid byte sequences become U+FFFD instead of failing the file. This
     * also stops the JDK parser from printing "[Fatal Error]" lines to stderr for bad bytes.
     */
    static Reader reader(byte[] content) {
        Charset charset = StandardCharsets.UTF_8;
        int offset = 0;
        if (content.length >= 3 && (content[0] & 0xff) == 0xEF && (content[1] & 0xff) == 0xBB && (content[2] & 0xff) == 0xBF) {
            offset = 3;
        } else if (content.length >= 2 && (content[0] & 0xff) == 0xFE && (content[1] & 0xff) == 0xFF) {
            charset = StandardCharsets.UTF_16BE;
            offset = 2;
        } else if (content.length >= 2 && (content[0] & 0xff) == 0xFF && (content[1] & 0xff) == 0xFE) {
            charset = StandardCharsets.UTF_16LE;
            offset = 2;
        } else {
            String head = new String(content, 0, Math.min(content.length, 200), StandardCharsets.ISO_8859_1);
            Matcher m = DECLARED_ENCODING.matcher(head);
            if (head.startsWith("<?xml") && m.find()) {
                try {
                    charset = Charset.forName(m.group(1));
                } catch (IllegalArgumentException unsupported) {
                    charset = StandardCharsets.UTF_8;
                }
            }
        }
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        return new InputStreamReader(new ByteArrayInputStream(content, offset, content.length - offset), decoder);
    }

    private static final Pattern DECLARED_ENCODING = Pattern.compile("encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");

    public ParseResult parse(List<ReportFile> files) {
        List<ReportFile> ordered = new ArrayList<>(files);
        ordered.sort(Comparator.comparing(ReportFile::name));

        Map<String, Builder> tests = new LinkedHashMap<>();
        List<ParseWarning> warnings = new ArrayList<>();
        for (ReportFile file : ordered) {
            new FileParser(file, tests, warnings).run();
        }
        if (warnings.size() > MAX_WARNINGS) {
            int extra = warnings.size() - MAX_WARNINGS;
            List<ParseWarning> capped = new ArrayList<>(warnings.subList(0, MAX_WARNINGS));
            capped.add(new ParseWarning("(all files)", null, extra + " more warnings were not shown"));
            warnings = capped;
        }
        List<ParsedTestCase> result = new ArrayList<>(tests.size());
        for (Builder b : tests.values()) {
            result.add(b.build());
        }
        return new ParseResult(result, warnings);
    }

    // ---------------------------------------------------------------------------------------------

    private static final class Builder {
        final String suite;
        final String name;
        String file;
        final List<ParsedExecution> executions = new ArrayList<>();

        Builder(String suite, String name) {
            this.suite = suite;
            this.name = name;
        }

        void add(List<ParsedExecution> more) {
            for (ParsedExecution e : more) {
                executions.add(e.withIndex(executions.size()));
            }
        }

        ParsedTestCase build() {
            return new ParsedTestCase(suite, name, file, executions);
        }
    }

    /** Text and attributes of one result element such as {@code <failure>}. */
    private record ResultElement(String kind, String message, String type, String time, String text) {}

    private final class FileParser {
        private final ReportFile file;
        private final Map<String, Builder> tests;
        private final List<ParseWarning> warnings;
        private final Deque<String> suiteNames = new ArrayDeque<>();
        private XMLStreamReader reader;
        private int depth;

        FileParser(ReportFile file, Map<String, Builder> tests, List<ParseWarning> warnings) {
            this.file = file;
            this.tests = tests;
            this.warnings = warnings;
        }

        void run() {
            try {
                reader = factory.createXMLStreamReader(reader(file.content()));
                try {
                    loop();
                } finally {
                    reader.close();
                }
            } catch (XMLStreamException e) {
                Integer line = e.getLocation() == null ? null : e.getLocation().getLineNumber();
                throw new ReportParseException(file.name(), line, cleanMessage(e.getMessage()));
            }
        }

        private void loop() throws XMLStreamException {
            boolean sawRoot = false;
            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.DTD ->
                        throw new ReportParseException(file.name(), line(), "DTDs are not allowed");
                    case XMLStreamConstants.ENTITY_REFERENCE ->
                        throw new ReportParseException(file.name(), line(), "entity references are not allowed");
                    case XMLStreamConstants.START_ELEMENT -> {
                        enter();
                        String local = reader.getLocalName();
                        if (!sawRoot) {
                            sawRoot = true;
                            if (!local.equals("testsuites") && !local.equals("testsuite")) {
                                throw new ReportParseException(
                                        file.name(), line(), "not a JUnit XML report (root element <" + local + ">)");
                            }
                        }
                        if (local.equals("testsuite")) {
                            suiteNames.push(nullToEmpty(reader.getAttributeValue(null, "name")));
                        } else if (local.equals("testcase")) {
                            readTestCase();
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        depth--;
                        if (reader.getLocalName().equals("testsuite") && !suiteNames.isEmpty()) {
                            suiteNames.pop();
                        }
                    }
                    default -> {
                        // text between elements, comments, processing instructions: ignored
                    }
                }
            }
            if (!sawRoot) {
                throw new ReportParseException(file.name(), null, "the file is empty");
            }
        }

        private void enter() {
            depth++;
            if (depth > limits.maxDepth()) {
                throw new ReportParseException(
                        file.name(), line(), "XML is nested deeper than " + limits.maxDepth() + " levels");
            }
        }

        /** Called on {@code <testcase>}; consumes everything up to and including its end tag. */
        private void readTestCase() throws XMLStreamException {
            int startLine = line();
            String rawName = reader.getAttributeValue(null, "name");
            String rawClass = reader.getAttributeValue(null, "classname");
            String time = reader.getAttributeValue(null, "time");
            String fileAttr = reader.getAttributeValue(null, "file");

            List<ResultElement> results = new ArrayList<>();
            StringBuilder systemErr = new StringBuilder();
            boolean[] trimmed = {false};
            int startDepth = depth;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    if (depth < startDepth) {
                        break;
                    }
                } else if (event == XMLStreamConstants.START_ELEMENT) {
                    enter();
                    String local = reader.getLocalName();
                    if (isResultElement(local)) {
                        results.add(readResultElement(local, trimmed));
                    } else if (local.equals("system-err")) {
                        systemErr.append(readText(trimmed));
                    }
                } else if (event == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw new ReportParseException(file.name(), line(), "entity references are not allowed");
                }
            }

            String name = TestKeys.tidy(rawName);
            if (name.isEmpty()) {
                warn(startLine, "skipped a <testcase> without a name");
                return;
            }
            String suite = TestKeys.tidy(rawClass);
            if (suite.isEmpty()) {
                suite = suiteNames.isEmpty() ? "" : TestKeys.tidy(suiteNames.peek());
                if (suite.isEmpty()) {
                    suite = "(unknown)";
                    warn(startLine, "test '" + name + "' has no classname or suite name; using (unknown)");
                }
            }
            if (trimmed[0]) {
                warn(startLine, "long text in test '" + name + "' was trimmed to " + limits.maxTextChars() + " characters");
            }

            long durationMs = parseMillis(time, startLine);
            List<ParsedExecution> executions = buildExecutions(results, durationMs, systemErr.toString());

            String key = suite + "\n" + name;
            Builder builder = tests.get(key);
            if (builder == null) {
                if (tests.size() >= limits.maxTestCases()) {
                    throw new ReportParseException(
                            file.name(), startLine, "more than " + limits.maxTestCases() + " test cases in one upload");
                }
                builder = new Builder(suite, name);
                tests.put(key, builder);
            }
            if (builder.file == null && fileAttr != null && !fileAttr.isBlank()) {
                builder.file = fileAttr.strip();
            }
            builder.add(executions);
        }

        private boolean isResultElement(String local) {
            return switch (local) {
                case "failure", "error", "skipped", "flakyFailure", "flakyError", "rerunFailure", "rerunError" -> true;
                default -> false;
            };
        }

        /** Reads a result element; its own text and any nested {@code <stackTrace>} text are kept. */
        private ResultElement readResultElement(String kind, boolean[] trimmed) throws XMLStreamException {
            String message = reader.getAttributeValue(null, "message");
            String type = reader.getAttributeValue(null, "type");
            String time = reader.getAttributeValue(null, "time");
            StringBuilder text = new StringBuilder();
            int startDepth = depth;
            boolean inStackTrace = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    if (depth < startDepth) {
                        break;
                    }
                    inStackTrace = false;
                } else if (event == XMLStreamConstants.START_ELEMENT) {
                    enter();
                    inStackTrace = reader.getLocalName().equals("stackTrace");
                } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    if (depth == startDepth || inStackTrace) {
                        append(text, reader.getText(), trimmed);
                    }
                } else if (event == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw new ReportParseException(file.name(), line(), "entity references are not allowed");
                }
            }
            return new ResultElement(kind, limitAttr(message, trimmed), type, time, text.toString());
        }

        /** Reads the text of the current element, including nested text, up to its end tag. */
        private String readText(boolean[] trimmed) throws XMLStreamException {
            StringBuilder text = new StringBuilder();
            int startDepth = depth;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    if (depth < startDepth) {
                        break;
                    }
                } else if (event == XMLStreamConstants.START_ELEMENT) {
                    enter();
                } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    append(text, reader.getText(), trimmed);
                }
            }
            return text.toString();
        }

        private void append(StringBuilder sb, String chunk, boolean[] trimmed) {
            int room = limits.maxTextChars() - sb.length();
            if (room <= 0) {
                if (!chunk.isEmpty()) {
                    trimmed[0] = true;
                }
                return;
            }
            if (chunk.length() > room) {
                sb.append(chunk, 0, room);
                trimmed[0] = true;
            } else {
                sb.append(chunk);
            }
        }

        private String limitAttr(String value, boolean[] trimmed) {
            if (value != null && value.length() > limits.maxTextChars()) {
                trimmed[0] = true;
                return value.substring(0, limits.maxTextChars());
            }
            return value;
        }

        private List<ParsedExecution> buildExecutions(List<ResultElement> results, long durationMs, String systemErr) {
            List<ParsedExecution> out = new ArrayList<>();
            ResultElement primary = null;
            ResultElement skipped = null;
            List<ResultElement> reruns = new ArrayList<>();
            for (ResultElement r : results) {
                switch (r.kind()) {
                    case "flakyFailure", "flakyError" -> out.add(failure(r, r.kind().equals("flakyError"), 0, ""));
                    case "failure", "error" -> {
                        if (primary == null) {
                            primary = r;
                        }
                    }
                    case "rerunFailure", "rerunError" -> reruns.add(r);
                    case "skipped" -> skipped = r;
                    default -> {
                        // not a result element
                    }
                }
            }
            if (primary != null) {
                out.add(failure(primary, primary.kind().equals("error"), durationMs, systemErr));
                for (ResultElement r : reruns) {
                    out.add(failure(r, r.kind().equals("rerunError"), parseMillis(r.time(), null), ""));
                }
            } else if (skipped != null) {
                String reason = firstNonBlank(skipped.message(), skipped.text().strip());
                if (reason != null && reason.startsWith(QUARANTINE_MARKER)) {
                    String rest = reason.substring(QUARANTINE_MARKER.length()).strip();
                    out.add(new ParsedExecution(
                            0, ExecutionStatus.FAILED, durationMs, null, trim(firstLine(rest), limits.maxMessageChars()),
                            trim(emptyToNull(rest), limits.maxStackTraceChars()), true));
                } else {
                    out.add(new ParsedExecution(0, ExecutionStatus.SKIPPED, durationMs, null, null, null, false));
                }
            } else {
                out.add(new ParsedExecution(0, ExecutionStatus.PASSED, durationMs, null, null, null, false));
            }
            return out;
        }

        private ParsedExecution failure(ResultElement r, boolean error, long durationMs, String fallbackStack) {
            String stack = r.text().strip();
            if (stack.isEmpty()) {
                stack = fallbackStack.strip();
            }
            String message = firstNonBlank(r.message(), firstLine(stack));
            String type = r.type() == null || r.type().isBlank() ? null : r.type().strip();
            return new ParsedExecution(
                    0,
                    error ? ExecutionStatus.ERRORED : ExecutionStatus.FAILED,
                    durationMs,
                    type,
                    trim(message, limits.maxMessageChars()),
                    trim(emptyToNull(stack), limits.maxStackTraceChars()),
                    false);
        }

        private long parseMillis(String time, Integer warnLine) {
            if (time == null || time.isBlank()) {
                return 0;
            }
            try {
                double seconds = Double.parseDouble(time.strip().replace(",", ""));
                if (Double.isNaN(seconds) || seconds < 0 || seconds > 1_000_000) {
                    throw new NumberFormatException();
                }
                return Math.round(seconds * 1000);
            } catch (NumberFormatException e) {
                if (warnLine != null) {
                    warn(warnLine, "invalid time value '" + trim(time, 40) + "'; using 0");
                }
                return 0;
            }
        }

        private void warn(Integer atLine, String message) {
            warnings.add(new ParseWarning(file.name(), atLine, message));
        }

        private int line() {
            return reader.getLocation() == null ? 0 : reader.getLocation().getLineNumber();
        }
    }

    private static String cleanMessage(String message) {
        if (message == null) {
            return "the file is not well-formed XML";
        }
        int idx = message.indexOf("Message: ");
        String clean = idx >= 0 ? message.substring(idx + "Message: ".length()) : message;
        return trim(clean.strip(), 300);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.strip();
        }
        return b == null || b.isBlank() ? null : b.strip();
    }

    private static String firstLine(String s) {
        if (s == null) {
            return null;
        }
        for (String line : s.split("\\R")) {
            if (!line.isBlank()) {
                return line.strip();
            }
        }
        return null;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static String trim(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
