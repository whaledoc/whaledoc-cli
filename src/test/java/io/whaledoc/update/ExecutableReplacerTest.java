package io.whaledoc.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutableReplacerTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldReplaceExecutableWhenPlatformIsUnix() throws IOException {

        // given
        Path executable = givenFileExists("whaledoc", "old version");
        Path replacement = givenFileExists("download/whaledoc", "new version");
        ExecutableReplacer executableReplacer = new ExecutableReplacer(Platform.LINUX_X64);

        // when
        executableReplacer.replace(executable, replacement);

        // then
        assertThat(executable).hasContent("new version");
        assertThat(tempDir.resolve("whaledoc.new")).doesNotExist();
    }

    @Test
    void shouldKeepPreviousVersionAsOldFileWhenPlatformIsWindows() throws IOException {

        // given
        Path executable = givenFileExists("whaledoc.exe", "old version");
        Path replacement = givenFileExists("download/whaledoc.exe", "new version");
        ExecutableReplacer executableReplacer = new ExecutableReplacer(Platform.WINDOWS_X64);

        // when
        executableReplacer.replace(executable, replacement);

        // then
        assertThat(executable).hasContent("new version");
        assertThat(tempDir.resolve("whaledoc.exe.old")).hasContent("old version");
    }

    @Test
    void shouldDeletePreviousVersionWhenItExists() throws IOException {

        // given
        Path executable = givenFileExists("whaledoc.exe", "new version");
        Path previousVersion = givenFileExists("whaledoc.exe.old", "old version");
        ExecutableReplacer executableReplacer = new ExecutableReplacer(Platform.WINDOWS_X64);

        // when
        executableReplacer.deletePreviousVersion(executable);

        // then
        assertThat(previousVersion).doesNotExist();
    }

    private Path givenFileExists(String relativePath, String content) throws IOException {

        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());

        return Files.writeString(file, content);
    }
}
