package io.whaledoc.exceptions;

public class ApiException extends RuntimeException {

    private final int statusCode;

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public ApiException(int statusCode, String responseBody) {
        super("API request failed with status " + statusCode + ": " + responseBody);
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
