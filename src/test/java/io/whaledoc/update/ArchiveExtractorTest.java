package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ArchiveExtractorTest {

    private static final String EXECUTABLE_CONTENT = "native-executable";

    @TempDir
    private Path tempDir;

    @Test
    void shouldExtractExecutableWhenArchiveIsZip() throws IOException {

        // given
        Path archive = givenZipArchiveExists("whaledoc.exe");
        ArchiveExtractor archiveExtractor = new ArchiveExtractor(Platform.WINDOWS_X64);

        // when
        Path actualExecutable = archiveExtractor.extractExecutable(archive);

        // then
        assertThat(actualExecutable)
                .isEqualTo(tempDir.resolve("whaledoc.exe"))
                .hasContent(EXECUTABLE_CONTENT);
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Release archives for Windows are zip files")
    void shouldExtractExecutableWhenArchiveIsTarGz() throws Exception {

        // given
        Path archive = givenTarGzArchiveExists();
        ArchiveExtractor archiveExtractor = new ArchiveExtractor(Platform.LINUX_X64);

        // when
        Path actualExecutable = archiveExtractor.extractExecutable(archive);

        // then
        assertThat(actualExecutable)
                .isEqualTo(tempDir.resolve("whaledoc"))
                .hasContent(EXECUTABLE_CONTENT);
    }

    @Test
    void shouldThrowUpdateExceptionWhenArchiveDoesNotContainExecutable() throws IOException {

        // given
        Path archive = givenZipArchiveExists("README.md");
        ArchiveExtractor archiveExtractor = new ArchiveExtractor(Platform.WINDOWS_X64);

        // when
        Throwable thrown = catchThrowable(() -> archiveExtractor.extractExecutable(archive));

        // then
        assertThat(thrown)
                .isInstanceOf(UpdateException.class)
                .hasMessage("whaledoc.exe is missing from whaledoc-windows-x64.zip.");
    }

    private Path givenZipArchiveExists(String entryName) throws IOException {

        Path archive = tempDir.resolve("whaledoc-windows-x64.zip");

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(EXECUTABLE_CONTENT.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        return archive;
    }

    private Path givenTarGzArchiveExists() throws Exception {

        Path source = Files.createDirectory(tempDir.resolve("source"));
        Files.writeString(source.resolve("whaledoc"), EXECUTABLE_CONTENT);
        Path archive = tempDir.resolve("whaledoc-linux-x64.tar.gz");

        Process tar = new ProcessBuilder("tar", "-czf", archive.toString(), "-C", source.toString(), "whaledoc")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        tar.getErrorStream().transferTo(OutputStream.nullOutputStream());
        assertThat(tar.waitFor()).isZero();

        return archive;
    }
}
