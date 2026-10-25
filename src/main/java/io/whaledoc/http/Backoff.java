package io.whaledoc.http;

import java.time.Duration;

/**
 * Retry bookkeeping for one connection: how many attempts are left and how long to wait before the next.
 *
 * <p>Before the first successful connection, only a couple of quick retries are made (1 s, 2 s), so an
 * unreachable server is reported within seconds. After the connection has been open, a dropped connection gets
 * up to 10 retries with exponential backoff (1 s, 2 s, 4 s … up to 30 s). {@link #reconnectingAtOnce()} tries the
 * first of those at once, as a closed WebSocket can usually reopen right away.
 */
final class Backoff {

    static final int MAX_FIRST_CONNECTION_RETRIES = 2;
    static final int MAX_RECONNECT_RETRIES = 10;

    private static final Duration INITIAL_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_DELAY = Duration.ofSeconds(30);
    /** Enough to pass {@link #MAX_DELAY}: 2^5 seconds. */
    private static final int MAX_DOUBLINGS = 5;

    private final boolean firstReconnectAtOnce;
    private boolean connectedBefore;
    private int attempt;
    private Duration delay = INITIAL_DELAY;

    Backoff() {
        this(false);
    }

    private Backoff(boolean firstReconnectAtOnce) {
        this.firstReconnectAtOnce = firstReconnectAtOnce;
    }

    static Backoff reconnectingAtOnce() {
        return new Backoff(true);
    }

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

        attempt++;
        delay = delayOf(attempt);

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

    private Duration delayOf(int attemptNumber) {

        boolean atOnce = firstReconnectAtOnce && connectedBefore;

        if (atOnce && attemptNumber == 1) {
            return Duration.ZERO;
        }

        int doublings = atOnce ? attemptNumber - 2 : attemptNumber - 1;
        Duration doubled = INITIAL_DELAY.multipliedBy(1L << Math.min(doublings, MAX_DOUBLINGS));
        return doubled.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : doubled;
    }
}
