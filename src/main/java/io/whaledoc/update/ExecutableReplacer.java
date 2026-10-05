package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Replaces the installed executable with a new version, including while it is running.
 */
@RequiredArgsConstructor
public final class ExecutableReplacer {

    private static final String STAGED_SUFFIX = ".new";
    private static final String PREVIOUS_SUFFIX = ".old";

    private final Platform platform;

    public void replace(Path executable, Path replacement) {

        Path staged = sibling(executable, STAGED_SUFFIX);

        try {
            // Stage next to the executable so the final move stays on the same file system
            Files.copy(replacement, staged, StandardCopyOption.REPLACE_EXISTING);
            staged.toFile().setExecutable(true, false);

            if (platform.isWindows()) {
                replaceRunningWindowsExecutable(executable, staged);
            } else {
                Files.move(staged, executable, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }

        } catch (AccessDeniedException e) {
            throw new UpdateException("Permission denied while updating %s. Try again with administrator rights, e.g. with sudo.".formatted(executable), e);

        } catch (IOException e) {
            throw new UpdateException("Unable to replace %s.".formatted(executable), e);

        } finally {
            deleteQuietly(staged);
        }
    }

    public void deletePreviousVersion(Path executable) {
        deleteQuietly(sibling(executable, PREVIOUS_SUFFIX));
    }

    // Windows can't overwrite a running executable, but it can rename it. The renamed
    // previous version is deleted the next time the CLI starts.
    private void replaceRunningWindowsExecutable(Path executable, Path staged) throws IOException {

        Path previous = sibling(executable, PREVIOUS_SUFFIX);

        Files.deleteIfExists(previous);
        Files.move(executable, previous);

        try {
            Files.move(staged, executable);

        } catch (IOException e) {
            Files.move(previous, executable);
            throw e;
        }
    }

    private static Path sibling(Path executable, String suffix) {
        return executable.resolveSibling(executable.getFileName() + suffix);
    }

    private static void deleteQuietly(Path file) {

        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Still in use or already gone; it is retried on the next start
        }
    }
}
