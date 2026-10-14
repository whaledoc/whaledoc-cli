package io.whaledoc.exceptions;

public class ApiException extends RuntimeException {

    private final int statusCode;

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /** A response outside 2xx; the message is what the user sees. */
    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ApiException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public int statusCode() {
        return statusCode;
    }
}
