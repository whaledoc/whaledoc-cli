package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ChecksumsTest {

    private static final String ARCHIVE_NAME = "whaledoc-linux-x64.tar.gz";
    private static final String ARCHIVE_CONTENT = "hello";
    private static final String RELEASE = "v1.4.0";
    private static final String ARCHIVE_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @TempDir
    private Path tempDir;

    @Test
    void shouldParseChecksumsWhenContentUsesSha256sumFormat() {

        // given
        String content = createChecksumsContent(ARCHIVE_SHA256);
        Checksums expectedChecksums = new Checksums(RELEASE, Map.of(
                ARCHIVE_NAME, ARCHIVE_SHA256,
                "install.sh", "0f343b0931126a20f133d67c2b018a3b5f5d1c0b0e5d4d2c6e8b9f2a1d3c4b5a"
        ));

        // when
        Checksums actualChecksums = Checksums.parse(content);

        // then
        assertThat(actualChecksums).usingRecursiveComparison().isEqualTo(expectedChecksums);
    }

    @Test
    void shouldAcceptFileWhenChecksumMatches() throws IOException {

        // given
        Path archive = givenArchiveExists();
        Checksums checksums = Checksums.parse(createChecksumsContent(ARCHIVE_SHA256));

        // when
        Throwable thrown = catchThrowable(() -> checksums.verify(archive));

        // then
        assertThat(thrown).isNull();
    }

    @Test
    void shouldThrowUpdateExceptionWhenChecksumDoesNotMatch() throws IOException {

        // given
        Path archive = givenArchiveExists();
        Checksums checksums = Checksums.parse(createChecksumsContent("0".repeat(64)));

        // when
        Throwable thrown = catchThrowable(() -> checksums.verify(archive));

        // then
        assertThat(thrown)
                .isInstanceOf(UpdateException.class)
                .hasMessageStartingWith("Checksum mismatch for " + ARCHIVE_NAME);
    }

    @Test
    void shouldThrowUpdateExceptionWhenFileHasNoChecksum() throws IOException {

        // given
        Path archive = givenArchiveExists();
        Checksums checksums = Checksums.parse("");

        // when
        Throwable thrown = catchThrowable(() -> checksums.verify(archive));

        // then
        assertThat(thrown)
                .isInstanceOf(UpdateException.class)
                .hasMessage("No checksum published for " + ARCHIVE_NAME + ".");
    }

    private Path givenArchiveExists() throws IOException {
        return Files.writeString(tempDir.resolve(ARCHIVE_NAME), ARCHIVE_CONTENT);
    }

    @Test
    void shouldAcceptReleaseWhenChecksumsNameIt() {

        // given
        Checksums checksums = Checksums.parse(createChecksumsContent(ARCHIVE_SHA256));

        // when
        Throwable thrown = catchThrowable(() -> checksums.verifyRelease(Version.parse("1.4.0")));

        // then
        assertThat(thrown).isNull();
    }

    @Test
    void shouldRefuseReleaseWhenChecksumsNameAnotherRelease() {

        // given
        Checksums checksums = Checksums.parse(createChecksumsContent(ARCHIVE_SHA256));

        // when
        Throwable thrown = catchThrowable(() -> checksums.verifyRelease(Version.parse("1.5.0")));

        // then
        assertThat(thrown)
                .isInstanceOf(UpdateException.class)
                .hasMessageStartingWith("The checksums don't belong to v1.5.0.");
    }

    @Test
    void shouldRefuseReleaseWhenChecksumsNameNoRelease() {

        // given
        Checksums checksums = Checksums.parse("%s  %s%n".formatted(ARCHIVE_SHA256, ARCHIVE_NAME));

        // when
        Throwable thrown = catchThrowable(() -> checksums.verifyRelease(Version.parse("1.4.0")));

        // then
        assertThat(thrown)
                .isInstanceOf(UpdateException.class)
                .hasMessageStartingWith("The checksums don't belong to v1.4.0.");
    }

    private String createChecksumsContent(String archiveSha256) {

        return """
                # whaledoc v1.4.0
                %s  %s
                0f343b0931126a20f133d67c2b018a3b5f5d1c0b0e5d4d2c6e8b9f2a1d3c4b5a  install.sh
                """.formatted(archiveSha256, ARCHIVE_NAME);
    }
}
