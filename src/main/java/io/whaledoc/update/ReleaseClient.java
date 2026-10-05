package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Reads releases published under a base URL with the GitHub Releases layout:
 * {@code {url}/latest/download/{file}} for the latest release and {@code {url}/download/v{version}/{file}}.
 */
@RequiredArgsConstructor
public final class ReleaseClient {

    private static final String VERSION_FILE = "version.txt";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final String releasesUrl;

    public ReleaseClient(String releasesUrl) {

        this(
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(TIMEOUT)
                        .build(),
                releasesUrl
        );
    }

    public Version fetchLatestVersion() {

        URI uri = URI.create("%s/latest/download/%s".formatted(releasesUrl, VERSION_FILE));
        String body = send(uri, BodyHandlers.ofString()).body();

        return Version.parse(body);
    }

    public Path download(Version version, String fileName, Path directory) {

        URI uri = URI.create("%s/download/v%s/%s".formatted(releasesUrl, version, fileName));
        Path target = directory.resolve(fileName);

        try {
            send(uri, BodyHandlers.ofFile(target));
            return target;

        } catch (UpdateException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    private <T> HttpResponse<T> send(URI uri, BodyHandler<T> bodyHandler) {

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            HttpResponse<T> response = httpClient.send(request, bodyHandler);

            if (response.statusCode() != 200) {
                throw new UpdateException("Unable to download %s (HTTP %d).".formatted(uri, response.statusCode()));
            }

            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpdateException("Download of %s was interrupted.".formatted(uri), e);

        } catch (IOException e) {
            throw new UpdateException("Unable to download %s. Check your internet connection.".formatted(uri), e);
        }
    }

    private static void deleteQuietly(Path file) {

        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Leftovers are removed with the temporary update directory
        }
    }
}
