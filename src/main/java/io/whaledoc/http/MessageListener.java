package io.whaledoc.http;

/**
 * Receives what happens on a WebSocket: each text message, and the drops and reconnects in between. Messages arrive one
 * at a time, in order, on a thread of their own, so handling one may take a while without holding up the connection.
 */
@FunctionalInterface
public interface MessageListener {

    void onMessage(String message);

    /**
     * The connection was open but dropped, and the client is about to try again.
     */
    default void onReconnecting(Reconnect reconnect) {
    }

    /**
     * The connection is open again after having dropped.
     */
    default void onReconnected() {
    }
}
