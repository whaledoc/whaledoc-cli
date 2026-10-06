package io.whaledoc.http;

import io.whaledoc.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;

/**
 * Connects to server-sent event streams and keeps them open. Each stream is read on its own virtual thread.
 *
 * <p>The first connection fails fast after a few quick attempts, because a server that can't be reached
 * at startup usually stays unreachable. Once a stream has been open, a dropped connection is retried
 * for longer with exponential backoff, and the listener is told about each attempt.
 */
@Slf4j
public final class SseClient {

    private static final String SSE_MEDIA_TYPE = "text/event-stream";

    private final HttpClient httpClient;
    private final ThreadFactory threadFactory;

    public SseClient(HttpClient httpClient) {

        this.httpClient = httpClient;
        this.threadFactory = Thread.ofVirtual()
                .name("whaledoc-sse-", 0)
                .factory();
    }

    public SseConnection connect(URI uri, SseListener listener) {
        return connect(uri, null, listener);
    }

    public SseConnection connect(URI uri, String accessToken, SseListener listener) {

        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        HttpRequest.Builder request = newRequest(uri, accessToken);
        SseConnection connection = new SseConnection();

        Thread readerThread = threadFactory.newThread(() -> run(request, connection, listener));
        connection.setReaderThread(readerThread);
        readerThread.start();

        return connection;
    }

    private void run(HttpRequest.Builder request, SseConnection connection, SseListener listener) {

        try {
            readUntilClosed(request, connection, listener);

        } catch (ApiException e) {
            connection.fail(e);

        } catch (RuntimeException e) {
            connection.fail(new ApiException("The event stream stopped unexpectedly.", e));
        }
    }

    private void readUntilClosed(HttpRequest.Builder request, SseConnection connection, SseListener listener) {

        SseEventReader reader = new SseEventReader(event -> dispatch(event, listener));
        Backoff backoff = new Backoff();

        while (connection.isOpen()) {

            ApiException dropReason;

            try {
                HttpRequest attempt = resumeFrom(request, reader.lastEventId());
                HttpResponse<InputStream> response = httpClient.send(attempt, HttpResponse.BodyHandlers.ofInputStream());

                validateResponse(response);
                connection.setInputStream(response.body());
                onConnected(connection, backoff, listener);

                reader.read(response.body(), connection::isOpen);
                dropReason = null;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;

            } catch (ApiException e) {

                if (!connection.isOpen() || !isRetryable(e)) {
                    throw e;
                }

                dropReason = e;

            } catch (IOException e) {

                if (!connection.isOpen()) {
                    return;
                }

                URI uri = request.copy().build().uri();
                dropReason = new ApiException(NetworkErrors.describe(uri, e), e);
            }

            if (!connection.isOpen() || !waitBeforeRetry(backoff, dropReason, listener)) {
                return;
            }
        }
    }

    private static void onConnected(SseConnection connection, Backoff backoff, SseListener listener) {

        boolean reconnected = backoff.hasConnectedBefore();

        backoff.connected();
        connection.markConnected();

        if (reconnected) {
            listener.onReconnected();
        }
    }

    /**
     * @return false when interrupted while waiting, i.e. when the connection was closed
     * @throws ApiException when there are no attempts left
     */
    private static boolean waitBeforeRetry(Backoff backoff, ApiException dropReason, SseListener listener) {

        if (!backoff.nextAttempt()) {
            throw giveUp(backoff, dropReason);
        }

        // Before the first connection the caller is still showing "connecting", so only drops are reported
        if (backoff.hasConnectedBefore()) {
            listener.onReconnecting(new Reconnect(backoff.attempt(), backoff.maxAttempts(), backoff.delay(), dropReason));
        }

        return sleep(backoff.delay().toMillis());
    }

    private static ApiException giveUp(Backoff backoff, ApiException dropReason) {

        String reason = dropReason == null ? "the server closed the connection." : dropReason.getMessage();

        if (!backoff.hasConnectedBefore()) {
            return dropReason != null ? dropReason : new ApiException("Unable to connect to WhaleDoc: " + reason);
        }

        return new ApiException("Lost the connection to WhaleDoc and couldn't reconnect: " + reason, dropReason);
    }

    // A failing handler must not end the stream; the next events should still arrive
    private static void dispatch(SseEvent event, SseListener listener) {

        try {
            listener.onEvent(event);
        } catch (RuntimeException e) {
            log.warn("Handling event {} failed", event.event(), e);
        }
    }

    // Server errors and rate limiting are temporary; rejected tokens and other client errors are not
    private static boolean isRetryable(ApiException e) {
        return e.statusCode() >= 500 || e.statusCode() == 429;
    }

    private static boolean sleep(long millis) {

        try {
            Thread.sleep(millis);
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static HttpRequest.Builder newRequest(URI uri, String accessToken) {

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .header("Accept", SSE_MEDIA_TYPE)
                .header("Cache-Control", "no-cache")
                .header("X-API-Version", ApiConstants.API_VERSION);

        if (StringUtils.isNotBlank(accessToken)) {
            request.header("Authorization", "Bearer " + accessToken);
        }

        return request;
    }

    // Asks the server to continue after the last received event, so none are missed while reconnecting
    private static HttpRequest resumeFrom(HttpRequest.Builder request, String lastEventId) {

        HttpRequest.Builder copy = request.copy();

        if (lastEventId != null) {
            copy.header("Last-Event-ID", lastEventId);
        }

        return copy.GET().build();
    }

    private static void validateResponse(HttpResponse<InputStream> response) throws IOException {

        int statusCode = response.statusCode();

        if (statusCode < 200 || statusCode >= 300) {

            try (InputStream body = response.body()) {
                throw new ApiException(statusCode, new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");

        if (!contentType.toLowerCase(Locale.ROOT).startsWith(SSE_MEDIA_TYPE)) {
            response.body().close();
            throw new ApiException("Invalid SSE response Content-Type: " + contentType);
        }
    }
}
