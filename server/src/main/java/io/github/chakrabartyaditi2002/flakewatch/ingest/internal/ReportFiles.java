package io.github.chakrabartyaditi2002.flakewatch.ingest.internal;

import io.github.chakrabartyaditi2002.flakewatch.core.report.ReportParser;
import io.github.chakrabartyaditi2002.flakewatch.platform.error.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reads uploaded report files safely (FR-ING-001, FR-ING-008, NFR-SEC-002, PLAN §7.2 steps 3–4).
 *
 * <p>Gzip is decompressed through a counting stream that stops at the limit, which defeats zip
 * bombs. Each file must start with {@code <testsuites>} or {@code <testsuite>}.
 */
public final class ReportFiles {

    public static final int MAX_FILES = 20;
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    public static final long MAX_DECOMPRESSED_FILE_BYTES = 100L * 1024 * 1024;
    public static final long MAX_DECOMPRESSED_TOTAL_BYTES = 200L * 1024 * 1024;

    private ReportFiles() {}

    public static List<ReceivedFile> read(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "no-files", "Attach at least one report file in the 'files' part");
        }
        if (files.size() > MAX_FILES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "too-many-files", "At most " + MAX_FILES + " files per upload");
        }
        List<ReceivedFile> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long total = 0;
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file.getSize() > MAX_FILE_BYTES) {
                throw tooLarge("File " + displayName(file, i) + " is larger than 20 MB");
            }
            byte[] raw = bytes(file);
            byte[] content = isGzip(raw) ? gunzip(raw, Math.min(MAX_DECOMPRESSED_FILE_BYTES, MAX_DECOMPRESSED_TOTAL_BYTES - total)) : raw;
            if (content.length > MAX_DECOMPRESSED_FILE_BYTES) {
                throw tooLarge("File " + displayName(file, i) + " is larger than 100 MB");
            }
            total += content.length;
            if (total > MAX_DECOMPRESSED_TOTAL_BYTES) {
                throw tooLarge("The files add up to more than 200 MB");
            }
            String name = uniqueName(displayName(file, i), names);
            if (!ReportParser.isJunitReport(content)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "not-a-junit-report",
                        "File " + name + " is not a JUnit XML report: it must start with <testsuites> or <testsuite>");
            }
            out.add(new ReceivedFile(name, content, sha256Hex(content)));
        }
        out.sort(Comparator.comparing(ReceivedFile::name));
        return out;
    }

    static String displayName(MultipartFile file, int index) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            return "report-" + (index + 1) + ".xml";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.endsWith(".gz")) {
            name = name.substring(0, name.length() - 3);
        }
        name = name.replaceAll("[^A-Za-z0-9._@+-]", "_");
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        return name.isBlank() ? "report-" + (index + 1) + ".xml" : name;
    }

    private static String uniqueName(String name, Set<String> taken) {
        String candidate = name;
        int n = 2;
        while (!taken.add(candidate)) {
            candidate = name + "~" + n++;
        }
        return candidate;
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean isGzip(byte[] raw) {
        return raw.length >= 2 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b;
    }

    /** Decompresses, but stops reading as soon as the output passes {@code limit}. */
    public static byte[] gunzip(byte[] raw, long limit) {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            long written = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                written += n;
                if (written > limit) {
                    throw tooLarge("A compressed file expands to more than the size limit");
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad-gzip", "A file looks gzip-compressed but can't be decompressed");
        }
    }

    public static byte[] gzip(byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, content.length / 4));
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static ApiException tooLarge(String detail) {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "payload-too-large", detail);
    }
}
