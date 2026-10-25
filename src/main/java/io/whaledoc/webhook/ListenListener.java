package io.whaledoc.webhook;

import io.whaledoc.http.Reconnect;

/**
 * Receives what happens while {@code whaledoc listen} is connected: each message, and the drops and reconnects in
 * between. Only {@link #onMessage} is required.
 */
@FunctionalInterface
public interface ListenListener {

    void onMessage(ListenMessage message);

    /**
     * The connection was open but dropped, and the CLI is about to try again.
     */
    default void onReconnecting(Reconnect reconnect) {
    }

    /**
     * The connection is open again after having dropped.
     */
    default void onReconnected() {
    }
}
