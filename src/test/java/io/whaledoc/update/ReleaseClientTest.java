package io.whaledoc.update;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.whaledoc.exceptions.UpdateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@WireMockTest
class ReleaseClientTest {

    private static final String ARCHIVE_NAME = "whaledoc-linux-x64.tar.gz";

    @TempDir
    private Path tempDir;

    @Test
    void shouldReturnLatestVersionWhenVersionFileIsPublished(WireMockRuntimeInfo wireMock) {

        // given
        givenLatestVersionIs("1.4.0\n");
        ReleaseClient releaseClient = createReleaseClient(wireMock);
        Version expectedVersion = new Version(1, 4, 0, null);

        // when
        Version actualVersion = releaseClient.fetchLatestVersion();

        // then
        assertThat(actualVersion).usingRecursiveComparison().isEqualTo(expectedVersion);
    }

    @Test
    void shouldFollowRedirectWhenDownloadingReleaseFile(WireMockRuntimeInfo wireMock) {

        // given
        givenReleaseFileRedirectsToStorage("archive-content");
        ReleaseClient releaseClient = createReleaseClient(wireMock);

        // when
        Path actualFile = releaseClient.download(Version.parse("1.4.0"), ARCHIVE_NAME, tempDir);

        // then
        assertThat(actualFile).hasContent("archive-content");
    }

    @Test
    void shouldThrowUpdateExceptionAndRemoveFileWhenReleaseFileIsNotFound(WireMockRuntimeInfo wireMock) {

        // given
        givenReleaseFileIsMissing();
        ReleaseClient releaseClient = createReleaseClient(wireMock);

        // when
        Throwable thrown = catchThrowable(() -> releaseClient.download(Version.parse("1.4.0"), ARCHIVE_NAME, tempDir));

        // then
        assertThat(thrown)
                .isInstanceOf(UpdateException.class)
                .hasMessageEndingWith("(HTTP 404).");
        assertThat(tempDir.resolve(ARCHIVE_NAME)).doesNotExist();
    }

    private void givenLatestVersionIs(String version) {
        stubFor(get(urlEqualTo("/releases/latest/download/version.txt")).willReturn(ok(version)));
    }

    // GitHub answers release downloads with a redirect to its file storage
    private void givenReleaseFileRedirectsToStorage(String content) {

        stubFor(get(urlEqualTo("/releases/download/v1.4.0/" + ARCHIVE_NAME))
                .willReturn(aResponse().withStatus(302).withHeader("Location", "/storage/" + ARCHIVE_NAME)));

        stubFor(get(urlEqualTo("/storage/" + ARCHIVE_NAME)).willReturn(ok(content)));
    }

    private void givenReleaseFileIsMissing() {
        stubFor(get(urlEqualTo("/releases/download/v1.4.0/" + ARCHIVE_NAME)).willReturn(notFound()));
    }

    private ReleaseClient createReleaseClient(WireMockRuntimeInfo wireMock) {

        HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        return new ReleaseClient(httpClient, wireMock.getHttpBaseUrl() + "/releases");
    }
}
