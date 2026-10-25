package io.whaledoc.http;

import io.whaledoc.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Opens a WebSocket to WhaleDoc and keeps it open, as {@code whaledoc listen} needs. Each connection is looked after by
 * its own virtual thread, and its messages are handled on another, one at a time and in order: a slow handler never
 * keeps the connection from answering WhaleDoc's pings.
 *
 * <p>The first connection fails fast after a few quick attempts, as an unreachable server at startup usually stays
 * unreachable. Once open, a dropped or closed connection is retried at once, then with exponential backoff, and the
 * listener is told about each attempt. A connection that brought nothing for 30 seconds, not even a ping, counts as
 * dropped.
 */
@Slf4j
public final class WebSocketClient {

    /** How long the messages received before a close may take to be handled before reconnecting: one forward. */
    private static final Duration HANDLER_DRAIN = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final ThreadFactory threadFactory;

    public WebSocketClient(HttpClient httpClient) {

        this.httpClient = httpClient;
        this.threadFactory = Thread.ofVirtual()
                .name("whaledoc-ws-", 0)
                .factory();
    }

    public WebSocketConnection connect(URI uri, String accessToken, MessageListener listener) {

        WebSocketConnection connection = new WebSocketConnection();
        Thread supervisor = threadFactory.newThread(() -> run(new Target(uri, accessToken), connection, listener));
        connection.setSupervisor(supervisor);
        supervisor.start();

        return connection;
    }

    private record Target(URI uri, String accessToken) {
    }

    private void run(Target target, WebSocketConnection connection, MessageListener listener) {

        ExecutorService handler = Executors.newSingleThreadExecutor(threadFactory);

        try {
            keepOpen(target, connection, listener, handler);

        } catch (ApiException e) {
            connection.fail(e);

        } catch (RuntimeException e) {
            connection.fail(new ApiException("The connection to WhaleDoc stopped unexpectedly.", e));

        } finally {
            handler.shutdown();
        }
    }

    private void keepOpen(Target target, WebSocketConnection connection, MessageListener listener,
                          ExecutorService handler) {

        Backoff backoff = Backoff.reconnectingAtOnce();

        while (connection.isOpen()) {

            ApiException dropReason;

            try {
                SocketSession session = new SocketSession(listener, handler);
                WebSocket socket = open(target, session);
                connection.setSocket(socket);
                onConnected(connection, backoff, listener);
                dropReason = session.awaitEnd(socket);
                drain(handler);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;

            } catch (ApiException e) {

                if (!connection.isOpen() || !isRetryable(e)) {
                    throw e;
                }

                dropReason = e;
            }

            if (!connection.isOpen() || !waitBeforeRetry(backoff, dropReason, listener)) {
                return;
            }
        }
    }

    private WebSocket open(Target target, SocketSession session) throws InterruptedException {

        try {
            return httpClient.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + target.accessToken())
                    .header(ApiConstants.API_VERSION_HEADER, ApiConstants.API_VERSION)
                    .buildAsync(target.uri(), session)
                    .get();

        } catch (ExecutionException e) {
            throw handshakeFailure(target.uri(), e.getCause());
        }
    }

    /** A refusal carries WhaleDoc's status and problem details, as an answer to any request does. */
    private static ApiException handshakeFailure(URI uri, Throwable cause) {

        return switch (cause) {
            case WebSocketHandshakeException refused -> refusal(refused.getResponse());
            case IOException io -> new ApiException(NetworkErrors.describe(uri, io), io);
            default -> new ApiException("Unable to connect to WhaleDoc.", cause);
        };
    }

    private static ApiException refusal(HttpResponse<?> response) {

        String body = switch (response.body()) {
            case byte[] bytes -> new String(bytes, StandardCharsets.UTF_8);
            case String text -> text;
            case null, default -> "";
        };
        return ErrorResponses.toException(response.statusCode(), body, response.headers());
    }

    /**
     * Lets the messages that came before a close be handled first: a {@code disconnected} message closes the
     * connection, and no reconnect may start before it did.
     */
    private static void drain(ExecutorService handler) throws InterruptedException {

        try {
            handler.submit(() -> { }).get(HANDLER_DRAIN.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            log.debug("Messages still being handled while reconnecting", e);
        }
    }

    private static void onConnected(WebSocketConnection connection, Backoff backoff, MessageListener listener) {

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
    private static boolean waitBeforeRetry(Backoff backoff, ApiException dropReason, MessageListener listener) {

        if (!backoff.nextAttempt()) {
            throw giveUp(backoff, dropReason);
        }

        // Before the first connection the caller is still showing "connecting", so only drops are reported
        if (backoff.hasConnectedBefore()) {
            listener.onReconnecting(new Reconnect(backoff.attempt(), backoff.maxAttempts(), backoff.delay(), dropReason));
        }

        return sleep(backoff.delay());
    }

    private static ApiException giveUp(Backoff backoff, ApiException dropReason) {

        String reason = dropReason == null ? "the server closed the connection." : dropReason.getMessage();

        if (!backoff.hasConnectedBefore()) {
            return dropReason != null ? dropReason : new ApiException("Unable to connect to WhaleDoc: " + reason);
        }

        return new ApiException("Lost the connection to WhaleDoc and couldn't reconnect: " + reason, dropReason);
    }

    // Server errors and rate limiting are temporary; rejected tokens and other client errors are not
    private static boolean isRetryable(ApiException e) {
        return e.statusCode() >= 500 || e.statusCode() == 429;
    }

    private static boolean sleep(Duration delay) {

        try {
            Thread.sleep(delay);
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
