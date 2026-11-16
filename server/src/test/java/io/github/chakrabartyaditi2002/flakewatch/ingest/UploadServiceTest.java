package io.github.chakrabartyaditi2002.flakewatch.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chakrabartyaditi2002.flakewatch.core.report.Fixtures;
import io.github.chakrabartyaditi2002.flakewatch.ingest.internal.ReceivedFile;
import io.github.chakrabartyaditi2002.flakewatch.ingest.internal.ReportFiles;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UploadServiceTest {

    private static UploadMetadata.Normalized metadata(String jobKey) {
        return new UploadMetadata("3f2a9c1d", "main", "GITHUB", "9876543210", 1, jobKey, null, null, null, null)
                .normalized();
    }

    private static ReceivedFile file(String name, byte[] content) {
        return new ReceivedFile(name, content, ReportFiles.sha256Hex(content));
    }

    @Test
    @DisplayName("FR-ING-004: the same files in a different order hash the same; different content doesn't")
    void contentHash() {
        byte[] a = Fixtures.bytes("maven-surefire/TEST-sample.SampleTest.xml");
        byte[] b = Fixtures.bytes("gradle/TEST-sample.SampleGradleTest.xml");
        byte[] h1 = UploadService.contentHash(metadata("unit"), List.of(file("a.xml", a), file("b.xml", b)));
        byte[] h2 = UploadService.contentHash(metadata("unit"), List.of(file("b.xml", b), file("a.xml", a)));
        byte[] h3 = UploadService.contentHash(metadata("unit"), List.of(file("a.xml", a), file("b.xml", a)));
        byte[] h4 = UploadService.contentHash(metadata("integration"), List.of(file("a.xml", a), file("b.xml", b)));
        assertThat(Arrays.equals(h1, h2)).isTrue();
        assertThat(Arrays.equals(h1, h3)).isFalse();
        assertThat(Arrays.equals(h1, h4)).isFalse();
    }

    @Test
    @DisplayName("FR-ING-002: optional metadata gets its documented defaults")
    void defaults() {
        UploadMetadata.Normalized m =
                new UploadMetadata("3f2a9c1d", " main ", null, "1", null, null, null, null, null, " ").normalized();
        assertThat(m.provider()).isEqualTo("OTHER");
        assertThat(m.runAttempt()).isEqualTo(1);
        assertThat(m.jobKey()).isEqualTo("default");
        assertThat(m.variant()).isEqualTo("");
        assertThat(m.branch()).isEqualTo("main");
        assertThat(m.repositoryUrl()).isNull();
    }
}
