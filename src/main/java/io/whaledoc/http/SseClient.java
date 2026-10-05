package io.whaledoc.http;

import io.whaledoc.exceptions.ApiException;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

public final class SseClient {

    private static final String SSE_MEDIA_TYPE = "text/event-stream";
    private static final long INITIAL_RECONNECT_DELAY_MILLIS = 1_000;
    private static final long MAX_RECONNECT_DELAY_MILLIS = 30_000;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private final HttpClient httpClient;
    private final ThreadFactory threadFactory;

    public SseClient() {

        this.httpClient = HttpClient.newHttpClient();
        this.threadFactory = Thread.ofVirtual()
                .name("whaledoc-sse-", 0)
                .factory();
    }

    public SseConnection connect(
            URI uri,
            Consumer<SseEvent> eventConsumer
    ) {
        return connect(uri, null, eventConsumer);
    }

    public SseConnection connect(URI uri, String accessToken, Consumer<SseEvent> eventConsumer) {

        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(eventConsumer, "eventConsumer must not be null");

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", SSE_MEDIA_TYPE)
                .header("Cache-Control", "no-cache")
                .header("X-API-Version", ApiConstants.API_VERSION);

        if (StringUtils.isNotBlank(accessToken)) {
            requestBuilder.header("Authorization", "Bearer " + accessToken);
        }

        HttpRequest request = requestBuilder
                .GET()
                .build();

        SseConnection connection = new SseConnection();

        Thread readerThread = threadFactory.newThread(
                () -> connectAndRead(request, connection, eventConsumer)
        );

        connection.setReaderThread(readerThread);
        readerThread.start();

        return connection;
    }

    private void connectAndRead(
            HttpRequest request,
            SseConnection connection,
            Consumer<SseEvent> eventConsumer
    ) {

        long reconnectDelay = INITIAL_RECONNECT_DELAY_MILLIS;
        int reconnectAttempts = 0;

        while (connection.isOpen()) {

            try {
                HttpResponse<InputStream> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofInputStream()
                        );

                validateResponse(response);

                if (!connection.isOpen()) {
                    response.body().close();
                    return;
                }

                connection.setInputStream(response.body());

                /*
                 * The HTTP connection was successfully established.
                 *
                 * Reset the reconnect state. If the server later
                 * closes the SSE stream, the next reconnect starts
                 * from the beginning of the backoff sequence.
                 */
                reconnectAttempts = 0;
                reconnectDelay = INITIAL_RECONNECT_DELAY_MILLIS;

                readEvents(response.body(), connection, eventConsumer);

                /*
                 * The server closed the SSE stream.
                 * Reconnect while the connection is still open.
                 */
                if (!connection.isOpen()) {
                    return;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;

            } catch (ApiException e) {
                if (!connection.isOpen()) {
                    return;
                }

                /*
                 * Authentication/authorization failures are not recoverable.
                 */
                if (e.statusCode() == 401 || e.statusCode() == 403) {
                    return;
                }

                /*
                 * Client-side errors are not recoverable.
                 */
                if (e.statusCode() < 500) {
                    return;
                }

            } catch (IOException e) {
                if (!connection.isOpen()) {
                    return;
                }
            }

            if (!connection.isOpen()) {
                return;
            }

            reconnectAttempts++;

            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                return;
            }

            try {
                Thread.sleep(reconnectDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            reconnectDelay = Math.min(
                    reconnectDelay * 2,
                    MAX_RECONNECT_DELAY_MILLIS
            );
        }
    }

    private void validateResponse(HttpResponse<InputStream> response) throws IOException {

        int statusCode = response.statusCode();

        if (statusCode < 200 || statusCode >= 300) {

            try (InputStream body = response.body()) {

                String responseBody = new String(
                        body.readAllBytes(),
                        StandardCharsets.UTF_8
                );

                throw new ApiException(statusCode, responseBody);
            }
        }

        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("");

        if (!contentType
                .toLowerCase()
                .startsWith(SSE_MEDIA_TYPE)) {

            response.body().close();

            throw new ApiException("Invalid SSE response Content-Type: " + contentType);
        }
    }

    private void readEvents(
            InputStream inputStream,
            SseConnection connection,
            Consumer<SseEvent> eventConsumer
    ) throws IOException {

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        )) {

            String event = null;
            String id = null;
            StringBuilder data = new StringBuilder();

            String line;

            while (connection.isOpen()
                   && (line = reader.readLine()) != null) {

                /*
                 * An empty line terminates an SSE event.
                 */
                if (line.isEmpty()) {

                    if (data.length() > 0) {
                        eventConsumer.accept(
                                new SseEvent(
                                        event,
                                        data.toString(),
                                        id
                                )
                        );
                    }

                    event = null;
                    id = null;
                    data.setLength(0);

                    continue;
                }

                /*
                 * SSE comments are commonly used as
                 * heartbeat/keep-alive messages.
                 *
                 * Example:
                 *
                 * : keep-alive
                 */
                if (line.startsWith(":")) {
                    continue;
                }

                if (line.startsWith("event:")) {
                    event = parseFieldValue(line);
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }

                    data.append(parseFieldValue(line));
                } else if (line.startsWith("id:")) {
                    id = parseFieldValue(line);
                }

                /*
                 * retry: is deliberately not handled here.
                 *
                 * Reconnection uses the client's own backoff policy.
                 */
            }
        }
    }

    private String parseFieldValue(String line) {
        int separator = line.indexOf(':');

        if (separator == -1) {
            return "";
        }

        String value = line.substring(separator + 1);

        if (value.startsWith(" ")) {
            value = value.substring(1);
        }

        return value;
    }
}