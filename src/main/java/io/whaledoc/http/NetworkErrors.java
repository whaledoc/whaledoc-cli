package io.whaledoc.http;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;

/**
 * Turns network failures into messages a user can act on. Java's HTTP client usually reports them
 * without a message, so the cause is identified by the exception types in the chain.
 */
public final class NetworkErrors {

    private NetworkErrors() {
    }

    public static String describe(URI uri, IOException error) {

        String server = uri.getAuthority();

        if (hasCause(error, HttpConnectTimeoutException.class)) {
            return "Connecting to %s timed out. Check your internet connection.".formatted(server);
        }

        if (hasCause(error, HttpTimeoutException.class)) {
            return "%s did not respond in time.".formatted(server);
        }

        if (hasCause(error, UnresolvedAddressException.class) || hasCause(error, UnknownHostException.class)) {
            return "Unable to find %s. Check your internet connection.".formatted(server);
        }

        if (hasCause(error, SSLException.class)) {
            return "Unable to set up a secure connection to %s.".formatted(server);
        }

        if (hasCause(error, ConnectException.class)) {
            return "Unable to connect to %s. Is the server running?".formatted(server);
        }

        return "Lost the connection to %s.".formatted(server);
    }

    private static boolean hasCause(Throwable error, Class<?> type) {

        for (Throwable cause = error; cause != null; cause = cause.getCause()) {

            if (type.isInstance(cause)) {
                return true;
            }
        }

        return false;
    }
}
