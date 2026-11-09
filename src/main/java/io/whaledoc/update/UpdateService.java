package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
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
    private static final String SIGNATURE_FILE = CHECKSUMS_FILE + ".sig";

    private final ReleaseClient releaseClient;
    private final ArchiveExtractor archiveExtractor;
    private final ExecutableReplacer executableReplacer;
    private final ReleaseSignature releaseSignature;
    private final Platform platform;

    public static UpdateService create(HttpClient httpClient, String releasesUrl) {

        Platform platform = Platform.current();

        return new UpdateService(
                new ReleaseClient(httpClient, releasesUrl),
                new ArchiveExtractor(platform),
                new ExecutableReplacer(platform),
                ReleaseSignature.official(),
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

    /** Checks that this executable can check release signatures, without downloading anything. */
    public void checkReleaseKey() {

        releaseSignature.checkKey();
    }

    public Version fetchLatestVersion() {
        return releaseClient.fetchLatestVersion();
    }

    public void install(Version version, Path executable) {

        Path directory = createTempDirectory();

        try {
            Path archive = releaseClient.download(version, platform.archiveName(), directory);
            Path checksumsFile = releaseClient.download(version, CHECKSUMS_FILE, directory);
            Path signatureFile = releaseClient.download(version, SIGNATURE_FILE, directory);

            Checksums checksums = readSignedChecksums(checksumsFile, signatureFile);
            checksums.verifyRelease(version);
            checksums.verify(archive);
            executableReplacer.replace(executable, archiveExtractor.extractExecutable(archive));

        } finally {
            deleteRecursively(directory);
        }
    }

    // The checksums are trusted only once their signature checks out: they come from the same release as the archive
    private Checksums readSignedChecksums(Path checksumsFile, Path signatureFile) {

        byte[] checksums = readBytes(checksumsFile);
        releaseSignature.verify(checksums, readBytes(signatureFile));
        return Checksums.parse(new String(checksums, StandardCharsets.UTF_8));
    }

    private static byte[] readBytes(Path file) {

        try {
            return Files.readAllBytes(file);

        } catch (IOException e) {
            throw new UpdateException("Unable to read " + file.getFileName() + ".", e);
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
