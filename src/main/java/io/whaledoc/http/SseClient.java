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
import java.util.function.Consumer;

/**
 * Connects to server-sent event streams and keeps them open, reconnecting with backoff when
 * the connection drops. Each stream is read on its own virtual thread.
 */
@Slf4j
public final class SseClient {

    private static final String SSE_MEDIA_TYPE = "text/event-stream";
    private static final long INITIAL_RECONNECT_DELAY_MILLIS = 1_000;
    private static final long MAX_RECONNECT_DELAY_MILLIS = 30_000;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private final HttpClient httpClient;
    private final ThreadFactory threadFactory;

    public SseClient(HttpClient httpClient) {

        this.httpClient = httpClient;
        this.threadFactory = Thread.ofVirtual()
                .name("whaledoc-sse-", 0)
                .factory();
    }

    public SseConnection connect(URI uri, Consumer<SseEvent> eventConsumer) {
        return connect(uri, null, eventConsumer);
    }

    public SseConnection connect(URI uri, String accessToken, Consumer<SseEvent> eventConsumer) {

        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(eventConsumer, "eventConsumer must not be null");

        HttpRequest.Builder request = newRequest(uri, accessToken);
        SseConnection connection = new SseConnection();

        Thread readerThread = threadFactory.newThread(() -> run(request, connection, eventConsumer));
        connection.setReaderThread(readerThread);
        readerThread.start();

        return connection;
    }

    private void run(HttpRequest.Builder request, SseConnection connection, Consumer<SseEvent> eventConsumer) {

        try {
            readUntilClosed(request, connection, eventConsumer);

        } catch (ApiException e) {
            connection.fail(e);

        } catch (RuntimeException e) {
            connection.fail(new ApiException("The event stream stopped unexpectedly.", e));
        }
    }

    private void readUntilClosed(HttpRequest.Builder request, SseConnection connection, Consumer<SseEvent> eventConsumer) {

        SseEventReader reader = new SseEventReader(event -> dispatch(event, eventConsumer));
        long reconnectDelay = INITIAL_RECONNECT_DELAY_MILLIS;
        int failedAttempts = 0;
        ApiException lastError = null;

        while (connection.isOpen()) {

            try {
                HttpResponse<InputStream> response = httpClient.send(
                        resumeFrom(request, reader.lastEventId()),
                        HttpResponse.BodyHandlers.ofInputStream()
                );

                validateResponse(response);
                connection.setInputStream(response.body());

                // Connected: a later reconnect starts from the beginning of the backoff sequence
                failedAttempts = 0;
                reconnectDelay = INITIAL_RECONNECT_DELAY_MILLIS;

                reader.read(response.body(), connection::isOpen);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;

            } catch (ApiException e) {

                if (!connection.isOpen() || !isRetryable(e)) {
                    throw e;
                }

                lastError = e;

            } catch (IOException e) {

                if (!connection.isOpen()) {
                    return;
                }

                lastError = new ApiException("Lost connection to WhaleDoc.", e);
            }

            if (++failedAttempts >= MAX_RECONNECT_ATTEMPTS) {
                throw new ApiException("Unable to reconnect to WhaleDoc after %d attempts.".formatted(MAX_RECONNECT_ATTEMPTS), lastError);
            }

            if (!sleep(reconnectDelay)) {
                return;
            }

            reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY_MILLIS);
        }
    }

    // A failing handler must not end the stream; the next events should still arrive
    private static void dispatch(SseEvent event, Consumer<SseEvent> eventConsumer) {

        try {
            eventConsumer.accept(event);
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
