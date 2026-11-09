package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UpdateServiceTest {

    private static final Version VERSION = Version.parse("1.4.0");
    private static final String ARCHIVE_NAME = "whaledoc-linux-x64.tar.gz";
    private static final String ARCHIVE_CONTENT = "archive-content";
    private static final KeyPair RELEASE_KEY = generateReleaseKey();

    @Mock
    private ReleaseClient releaseClient;

    @Mock
    private ArchiveExtractor archiveExtractor;

    @Mock
    private ExecutableReplacer executableReplacer;

    @TempDir
    private Path tempDir;

    private UpdateService updateService;

    @BeforeEach
    void setUp() {
        updateService = new UpdateService(
                releaseClient,
                archiveExtractor,
                executableReplacer,
                new ReleaseSignature(RELEASE_KEY.getPublic().getEncoded()),
                Platform.LINUX_X64
        );
    }

    @Test
    void shouldReplaceExecutableWhenArchiveMatchesChecksum() throws Exception {

        // given
        givenReleaseFileIsPublished(ARCHIVE_NAME, ARCHIVE_CONTENT);
        givenSignedChecksumsArePublished(createChecksumsContent(sha256(ARCHIVE_CONTENT)));
        Path executable = tempDir.resolve("whaledoc");
        Path extractedExecutable = tempDir.resolve("extracted/whaledoc");
        given(archiveExtractor.extractExecutable(any())).willReturn(extractedExecutable);

        // when
        updateService.install(VERSION, executable);

        // then
        then(executableReplacer).should().replace(executable, extractedExecutable);
    }

    @Test
    void shouldNotReplaceExecutableWhenChecksumDoesNotMatch() throws Exception {

        // given
        givenReleaseFileIsPublished(ARCHIVE_NAME, "tampered-content");
        givenSignedChecksumsArePublished(createChecksumsContent(sha256(ARCHIVE_CONTENT)));

        // when
        Throwable thrown = catchThrowable(() -> updateService.install(VERSION, tempDir.resolve("whaledoc")));

        // then
        assertThat(thrown)
                .isInstanceOf(UpdateException.class)
                .hasMessageStartingWith("Checksum mismatch for " + ARCHIVE_NAME);
        then(executableReplacer).shouldHaveNoInteractions();
    }

    @Test
    void shouldRemoveDownloadedFilesWhenInstallationFinishes() throws Exception {

        // given
        givenReleaseFileIsPublished(ARCHIVE_NAME, ARCHIVE_CONTENT);
        givenSignedChecksumsArePublished(createChecksumsContent(sha256(ARCHIVE_CONTENT)));
        given(archiveExtractor.extractExecutable(any())).willReturn(tempDir.resolve("extracted/whaledoc"));
        ArgumentCaptor<Path> downloadDirectory = ArgumentCaptor.forClass(Path.class);

        // when
        updateService.install(VERSION, tempDir.resolve("whaledoc"));

        // then
        then(releaseClient).should().download(eq(VERSION), eq(ARCHIVE_NAME), downloadDirectory.capture());
        assertThat(downloadDirectory.getValue()).doesNotExist();
    }

    @Test
    void shouldNotReplaceExecutableWhenChecksumsAreNotSignedByReleaseKey() throws Exception {

        // given
        String checksums = createChecksumsContent(sha256("other-archive"));
        givenReleaseFileIsPublished(ARCHIVE_NAME, "other-archive");
        givenReleaseFileIsPublished("checksums.txt", checksums);
        givenReleaseFileIsPublished("checksums.txt.sig", ReleaseSignatureTest.sign(bytesOf(checksums), generateReleaseKey()));

        // when
        Throwable thrown = catchThrowable(() -> updateService.install(VERSION, tempDir.resolve("whaledoc")));

        // then
        assertThat(thrown)
                .isInstanceOf(UpdateException.class)
                .hasMessageStartingWith("The release isn't signed by WhaleDoc.");
        then(executableReplacer).shouldHaveNoInteractions();
    }

    @Test
    void shouldNotReplaceExecutableWhenSignedChecksumsBelongToAnotherRelease() throws Exception {

        // given
        givenReleaseFileIsPublished(ARCHIVE_NAME, ARCHIVE_CONTENT);
        givenSignedChecksumsArePublished("# whaledoc v1.3.0%n%s  %s%n".formatted(sha256(ARCHIVE_CONTENT), ARCHIVE_NAME));

        // when
        Throwable thrown = catchThrowable(() -> updateService.install(VERSION, tempDir.resolve("whaledoc")));

        // then
        assertThat(thrown)
                .isInstanceOf(UpdateException.class)
                .hasMessageStartingWith("The checksums don't belong to v1.4.0.");
        then(executableReplacer).shouldHaveNoInteractions();
    }

    private void givenSignedChecksumsArePublished(String checksums) throws GeneralSecurityException {

        givenReleaseFileIsPublished("checksums.txt", checksums);
        givenReleaseFileIsPublished("checksums.txt.sig", ReleaseSignatureTest.sign(bytesOf(checksums), RELEASE_KEY));
    }

    private void givenReleaseFileIsPublished(String fileName, String content) {

        givenReleaseFileIsPublished(fileName, bytesOf(content));
    }

    private void givenReleaseFileIsPublished(String fileName, byte[] content) {

        given(releaseClient.download(eq(VERSION), eq(fileName), any())).willAnswer(invocation -> {
            Path directory = invocation.getArgument(2);
            return Files.write(directory.resolve(fileName), content);
        });
    }

    private static byte[] bytesOf(String content) {

        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static KeyPair generateReleaseKey() {

        try {
            return ReleaseSignatureTest.generateKeyPair();

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 is part of every JDK", e);
        }
    }

    private String createChecksumsContent(String archiveSha256) {
        return "# whaledoc v%s%n%s  %s%n".formatted(VERSION, archiveSha256, ARCHIVE_NAME);
    }

    private String sha256(String content) throws Exception {

        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytesOf(content));
        return HexFormat.of().formatHex(digest);
    }
}
