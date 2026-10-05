package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts the executable from a release archive into the archive's directory.
 */
@RequiredArgsConstructor
public final class ArchiveExtractor {

    private final Platform platform;

    public Path extractExecutable(Path archive) {

        String executableName = platform.getExecutableName();
        Path executable = archive.resolveSibling(executableName);

        try {
            if (archive.getFileName().toString().endsWith(".zip")) {
                extractFromZip(archive, executable);
            } else {
                extractFromTarGz(archive, executableName);
            }

        } catch (IOException e) {
            throw new UpdateException("Unable to extract " + archive.getFileName() + ".", e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpdateException("Extracting " + archive.getFileName() + " was interrupted.", e);
        }

        if (!Files.isRegularFile(executable)) {
            throw new UpdateException(executableName + " is missing from " + archive.getFileName() + ".");
        }

        return executable;
    }

    private void extractFromZip(Path archive, Path executable) throws IOException {

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {

            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {

                if (entry.getName().equals(executable.getFileName().toString())) {
                    Files.copy(zip, executable, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
    }

    // macOS and Linux always ship tar; run it inside the archive's directory to avoid path quirks
    private void extractFromTarGz(Path archive, String executableName) throws IOException, InterruptedException {

        Process process = new ProcessBuilder("tar", "-xzf", archive.getFileName().toString(), executableName)
                .directory(archive.getParent().toFile())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        if (process.waitFor() != 0) {
            throw new UpdateException("Unable to extract %s: %s".formatted(archive.getFileName(), output.strip()));
        }
    }
}
