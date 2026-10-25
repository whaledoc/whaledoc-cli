package io.whaledoc.http;

import io.whaledoc.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;

import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A WebSocket kept open by {@link WebSocketClient}, across reconnects. It ends either when it is closed, with a
 * normal close, or when it can't be kept open, for example because the access token was rejected
 * or reconnecting kept failing.
 */
@Slf4j
public final class WebSocketConnection implements AutoCloseable {

    /** How long writing the close frame may take before the connection is cut. */
    private static final Duration CLOSE_WAIT = Duration.ofSeconds(1);
    /** How long writing one message may take before it is dropped. */
    private static final Duration SEND_WAIT = Duration.ofSeconds(1);

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CompletableFuture<Void> connected = new CompletableFuture<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    private volatile WebSocket socket;
    private volatile Thread supervisor;

    void setSocket(WebSocket socket) {
        this.socket = socket;
    }

    void setSupervisor(Thread supervisor) {
        this.supervisor = supervisor;
    }

    void markConnected() {
        connected.complete(null);
    }

    void fail(ApiException error) {

        closed.set(true);
        connected.completeExceptionally(error);
        completion.completeExceptionally(error);
    }

    /**
     * Completes once the connection is open for the first time, or exceptionally with an {@link ApiException} when it
     * could not be opened.
     */
    public CompletableFuture<Void> connected() {
        return connected.copy();
    }

    /**
     * Waits until the connection ends.
     *
     * @throws ApiException when it ended because of an error
     */
    public void awaitCompletion() throws InterruptedException {

        try {
            completion.get();

        } catch (ExecutionException e) {

            if (e.getCause() instanceof ApiException apiException) {
                throw apiException;
            }

            throw new ApiException("The connection to WhaleDoc failed.", e.getCause());
        }
    }

    public boolean isOpen() {
        return !closed.get();
    }

    /** Sends a text message if the connection is open right now; one that can't be sent is dropped. */
    public synchronized void send(String message) {

        Optional.ofNullable(socket)
                .filter(open -> !open.isOutputClosed())
                .ifPresent(open -> sendNow(open, message));
    }

    /** Ctrl+C or the terminal closing: a normal close rather than a dropped connection. */
    @Override
    public void close() {

        if (!closed.compareAndSet(false, true)) {
            return;
        }

        Optional.ofNullable(socket).ifPresent(WebSocketConnection::closeNormally);
        Optional.ofNullable(supervisor).ifPresent(Thread::interrupt);
        connected.cancel(false);
        completion.complete(null);
    }

    private static void sendNow(WebSocket socket, String message) {

        try {
            socket.sendText(message, true).get(SEND_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            log.debug("A message to WhaleDoc was not sent", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeNormally(WebSocket socket) {

        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "").get(CLOSE_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            log.debug("The close didn't reach WhaleDoc", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            socket.abort();
        }
    }
}
