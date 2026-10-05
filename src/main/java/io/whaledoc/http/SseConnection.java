package io.whaledoc.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SseConnection implements AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CountDownLatch completion = new CountDownLatch(1);

    private volatile InputStream inputStream;
    private volatile Thread readerThread;

    void setInputStream(InputStream inputStream) {
        this.inputStream = Objects.requireNonNull(inputStream);
    }

    void setReaderThread(Thread readerThread) {
        this.readerThread = Objects.requireNonNull(readerThread);
    }

    void complete() {
        completion.countDown();
    }

    public void awaitCompletion() throws InterruptedException {
        completion.await();
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
        completion.countDown();
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