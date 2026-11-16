package io.github.chakrabartyaditi2002.flakewatch.ingest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.chakrabartyaditi2002.flakewatch.core.report.Fixtures;
import io.github.chakrabartyaditi2002.flakewatch.platform.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ReportFilesTest {

    private static final byte[] SUREFIRE = Fixtures.bytes("maven-surefire/TEST-sample.SampleTest.xml");

    private static MultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "application/xml", content);
    }

    private static String code(Runnable action) {
        try {
            action.run();
            return "no error";
        } catch (ApiException e) {
            return e.getCode();
        }
    }

    @Test
    @DisplayName("FR-ING-001: plain and gzip files are read; names lose their folders and .gz, and are sorted")
    void readsPlainAndGzip() {
        List<ReceivedFile> files = ReportFiles.read(List.of(
                file("C:\\work\\shop\\target\\surefire-reports\\TEST-b.xml", SUREFIRE),
                file("target/surefire-reports/TEST-a.xml.gz", ReportFiles.gzip(SUREFIRE))));
        assertThat(files.stream().map(ReceivedFile::name).toList()).containsExactly("TEST-a.xml", "TEST-b.xml");
        assertThat(files.get(0).sha256()).isEqualTo(files.get(1).sha256());
    }

    @Test
    @DisplayName("FR-ING-001: two reports with the same file name from different modules both survive")
    void duplicateNamesAreKept() {
        List<ReceivedFile> files = ReportFiles.read(List.of(
                file("api/target/surefire-reports/TEST-x.xml", SUREFIRE),
                file("web/target/surefire-reports/TEST-x.xml", SUREFIRE)));
        assertThat(files.stream().map(ReceivedFile::name).toList()).containsExactly("TEST-x.xml", "TEST-x.xml~2");
    }

    @Test
    @DisplayName("FR-ING-008: more than 20 files, no files, or a file that isn't a JUnit report are refused")
    void refusesBadFileSets() {
        List<MultipartFile> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            many.add(file("TEST-" + i + ".xml", SUREFIRE));
        }
        assertThat(code(() -> ReportFiles.read(many))).isEqualTo("too-many-files");
        assertThat(code(() -> ReportFiles.read(List.of()))).isEqualTo("no-files");
        assertThat(code(() -> ReportFiles.read(List.of(file("coverage.xml", "<coverage/>".getBytes())))))
                .isEqualTo("not-a-junit-report");
    }

    @Test
    @DisplayName("NFR-SEC-002: a zip bomb stops being decompressed at the limit")
    void zipBombIsStopped() {
        byte[] bomb = ReportFiles.gzip(new byte[5 * 1024 * 1024]);
        assertThat(bomb.length).isLessThan(10_000);
        assertThatThrownBy(() -> ReportFiles.gunzip(bomb, 1024 * 1024))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expands to more than");
    }

    @Test
    @DisplayName("FR-ING-008: a corrupt gzip file is a clear 400")
    void corruptGzip() {
        byte[] corrupt = ReportFiles.gzip(SUREFIRE);
        corrupt[corrupt.length / 2] ^= 0x55;
        corrupt[corrupt.length - 5] ^= 0x55;
        assertThat(code(() -> ReportFiles.read(List.of(file("TEST-a.xml.gz", corrupt))))).isEqualTo("bad-gzip");
    }
}
