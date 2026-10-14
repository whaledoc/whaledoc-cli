package io.whaledoc.exceptions;

import java.time.Duration;
import java.util.Optional;

public class ApiException extends RuntimeException {

    private final int statusCode;
    private final Duration retryAfter;

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.retryAfter = null;
    }

    /** A response outside 2xx; the message is what the user sees. */
    public ApiException(int statusCode, String message) {
        this(statusCode, message, null);
    }

    /** A response outside 2xx that says how long to wait before trying again, such as a 429. */
    public ApiException(int statusCode, String message, Duration retryAfter) {
        super(message);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    public ApiException(String message) {
        super(message);
        this.statusCode = -1;
        this.retryAfter = null;
    }

    public int statusCode() {
        return statusCode;
    }

    /** How long the API asked to wait, from its {@code Retry-After} header. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
