package io.whaledoc.exceptions;

/**
 * Forwarding a webhook event to the user's local endpoint failed.
 */
public class ForwardException extends RuntimeException {

    public ForwardException(String message) {
        super(message);
    }

    public ForwardException(String message, Throwable cause) {
        super(message, cause);
    }
}
