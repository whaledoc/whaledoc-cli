package io.whaledoc.http;

import io.whaledoc.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * One opened WebSocket: hands complete text messages to the handler thread, notes when WhaleDoc was last heard
 * from, and ends with the close or the error. Pings are answered by the JDK's WebSocket itself.
 */
@Slf4j
final class SocketSession implements WebSocket.Listener {

    private static final Duration SILENCE_LIMIT = Duration.ofSeconds(30);
    private static final Duration SILENCE_CHECK = Duration.ofSeconds(5);

    private final MessageListener listener;
    private final ExecutorService handler;
    private final StringBuilder partial = new StringBuilder();
    /** Null when the server closed the connection, the reason when it failed. */
    private final CompletableFuture<ApiException> end = new CompletableFuture<>();
    private volatile Instant lastHeard = Instant.now();

    SocketSession(MessageListener listener, ExecutorService handler) {

        this.listener = listener;
        this.handler = handler;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {

        heard(webSocket);
        partial.append(data);

        if (last) {
            String message = partial.toString();
            partial.setLength(0);
            handler.execute(() -> dispatch(message));
        }

        return null;
    }

    @Override
    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {

        heard(webSocket);
        return null;
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {

        heard(webSocket);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {

        heard(webSocket);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {

        log.debug("WhaleDoc closed the connection [code={}, reason={}]", statusCode, reason);
        end.complete(null);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {

        end.complete(new ApiException("The connection to WhaleDoc failed.", error));
    }

    /** Waits for the close, the error, or 30 seconds without a word from WhaleDoc. */
    ApiException awaitEnd(WebSocket socket) throws InterruptedException {

        while (true) {
            try {
                return end.get(SILENCE_CHECK.toMillis(), TimeUnit.MILLISECONDS);

            } catch (TimeoutException e) {

                if (Duration.between(lastHeard, Instant.now()).compareTo(SILENCE_LIMIT) > 0) {
                    socket.abort();
                    return new ApiException("WhaleDoc stopped answering.");
                }

            } catch (ExecutionException e) {
                return new ApiException("The connection to WhaleDoc failed.", e.getCause());
            }
        }
    }

    private void heard(WebSocket webSocket) {

        lastHeard = Instant.now();
        webSocket.request(1);
    }

    // A failing handler must not end the connection; the next messages should still arrive
    private void dispatch(String message) {

        try {
            listener.onMessage(message);
        } catch (RuntimeException e) {
            log.warn("Handling a message from WhaleDoc failed", e);
        }
    }
}
