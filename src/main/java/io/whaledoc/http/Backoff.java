package io.whaledoc.http;

import java.time.Duration;

/**
 * Retry bookkeeping for one event stream: how many attempts are left and how long to wait before the next.
 *
 * <p>Before the first successful connection, only a couple of quick retries are made (1 s, 2 s), so an
 * unreachable server is reported within seconds. After the stream has been open, a dropped connection gets
 * up to 10 retries with exponential backoff (1 s, 2 s, 4 s … up to 30 s).
 */
final class Backoff {

    static final int MAX_FIRST_CONNECTION_RETRIES = 2;
    static final int MAX_RECONNECT_RETRIES = 10;

    private static final Duration INITIAL_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_DELAY = Duration.ofSeconds(30);

    private boolean connectedBefore;
    private int attempt;
    private Duration delay = INITIAL_DELAY;

    void connected() {
        connectedBefore = true;
        attempt = 0;
        delay = INITIAL_DELAY;
    }

    /**
     * Moves on to the next retry.
     *
     * @return false when no retries are left
     */
    boolean nextAttempt() {

        if (attempt > 0) {
            delay = delay.multipliedBy(2).compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay.multipliedBy(2);
        }

        attempt++;

        return attempt <= maxAttempts();
    }

    boolean hasConnectedBefore() {
        return connectedBefore;
    }

    int attempt() {
        return attempt;
    }

    int maxAttempts() {
        return connectedBefore ? MAX_RECONNECT_RETRIES : MAX_FIRST_CONNECTION_RETRIES;
    }

    Duration delay() {
        return delay;
    }
}
