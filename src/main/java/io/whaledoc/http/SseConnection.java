package io.whaledoc.http;

import io.whaledoc.exceptions.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An open event stream. It ends either when it is closed, or when the stream can't be kept open,
 * for example because the access token was rejected or reconnecting kept failing.
 */
public final class SseConnection implements AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CompletableFuture<Void> connected = new CompletableFuture<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    private volatile InputStream inputStream;
    private volatile Thread readerThread;

    void setInputStream(InputStream inputStream) {
        this.inputStream = Objects.requireNonNull(inputStream);
    }

    void setReaderThread(Thread readerThread) {
        this.readerThread = Objects.requireNonNull(readerThread);
    }

    void markConnected() {
        connected.complete(null);
    }

    void fail(ApiException error) {

        if (closed.compareAndSet(false, true)) {
            closeInputStream();
        }

        connected.completeExceptionally(error);
        completion.completeExceptionally(error);
    }

    /**
     * Completes once the stream is open for the first time, or exceptionally with an {@link ApiException}
     * when it could not be opened.
     */
    public CompletableFuture<Void> connected() {
        return connected.copy();
    }

    /**
     * Completes normally when the connection is closed, or exceptionally with an {@link ApiException}
     * when the stream ended because of an error.
     */
    public CompletableFuture<Void> completion() {
        return completion.copy();
    }

    /**
     * Waits until the connection ends.
     *
     * @throws ApiException when the stream ended because of an error
     */
    public void awaitCompletion() throws InterruptedException {

        try {
            completion.get();

        } catch (ExecutionException e) {

            if (e.getCause() instanceof ApiException apiException) {
                throw apiException;
            }

            throw new ApiException("The event stream failed.", e.getCause());
        }
    }

    public boolean isOpen() {
        return !closed.get();
    }

    @Override
    public void close() {

        if (!closed.compareAndSet(false, true)) {
            return;
        }

        closeInputStream();
        interruptReaderThread();
        connected.cancel(false);
        completion.complete(null);
    }

    private void closeInputStream() {

        InputStream input = inputStream;

        if (input == null) {
            return;
        }

        try {
            input.close();
        } catch (IOException ignored) {
            // The connection is already being closed.
        }
    }

    private void interruptReaderThread() {

        Thread thread = readerThread;

        if (thread != null) {
            thread.interrupt();
        }
    }
}
