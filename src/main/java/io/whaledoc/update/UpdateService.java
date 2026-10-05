package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Downloads a release, verifies it and installs it in place of the current executable.
 */
@Slf4j
@RequiredArgsConstructor
public final class UpdateService {

    private static final String CHECKSUMS_FILE = "checksums.txt";

    private final ReleaseClient releaseClient;
    private final ArchiveExtractor archiveExtractor;
    private final ExecutableReplacer executableReplacer;
    private final Platform platform;

    public static UpdateService create(HttpClient httpClient, String releasesUrl) {

        Platform platform = Platform.current();

        return new UpdateService(
                new ReleaseClient(httpClient, releasesUrl),
                new ArchiveExtractor(platform),
                new ExecutableReplacer(platform),
                platform
        );
    }

    // Removes the executable a previous update on Windows renamed but couldn't delete while it was running
    public static void cleanUpPreviousVersion() {

        if (!CurrentExecutable.isNativeImage()) {
            return;
        }

        try {
            Platform platform = Platform.current();

            if (platform.isWindows()) {
                new ExecutableReplacer(platform).deletePreviousVersion(CurrentExecutable.path());
            }

        } catch (UpdateException e) {
            log.debug("Unable to clean up previous version", e);
        }
    }

    public Version fetchLatestVersion() {
        return releaseClient.fetchLatestVersion();
    }

    public void install(Version version, Path executable) {

        Path directory = createTempDirectory();

        try {
            Path archive = releaseClient.download(version, platform.archiveName(), directory);
            Path checksumsFile = releaseClient.download(version, CHECKSUMS_FILE, directory);

            readChecksums(checksumsFile).verify(archive);
            executableReplacer.replace(executable, archiveExtractor.extractExecutable(archive));

        } finally {
            deleteRecursively(directory);
        }
    }

    private static Checksums readChecksums(Path checksumsFile) {

        try {
            return Checksums.parse(Files.readString(checksumsFile));

        } catch (IOException e) {
            throw new UpdateException("Unable to read " + CHECKSUMS_FILE + ".", e);
        }
    }

    private static Path createTempDirectory() {

        try {
            return Files.createTempDirectory("whaledoc-update");

        } catch (IOException e) {
            throw new UpdateException("Unable to create a temporary directory for the update.", e);
        }
    }

    private static void deleteRecursively(Path directory) {

        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(UpdateService::deleteQuietly);

        } catch (IOException e) {
            log.debug("Unable to remove {}", directory, e);
        }
    }

    private static void deleteQuietly(Path file) {

        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.debug("Unable to remove {}", file, e);
        }
    }
}
