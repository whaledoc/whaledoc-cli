package io.whaledoc.http;

import java.util.function.Predicate;

/**
 * Receives what happens on an event stream. Only {@link #onEvent} is required, so a lambda works
 * when the connection state isn't of interest.
 */
@FunctionalInterface
public interface SseListener {

    void onEvent(SseEvent event);

    /**
     * The stream was open but dropped, and the client waits before trying again.
     */
    default void onReconnecting(Reconnect reconnect) {
    }

    /**
     * The stream is open again after having dropped.
     */
    default void onReconnected() {
    }

    /**
     * Passes on only the events that match, together with all connection updates.
     */
    default SseListener filter(Predicate<SseEvent> predicate) {

        SseListener listener = this;

        return new SseListener() {

            @Override
            public void onEvent(SseEvent event) {

                if (predicate.test(event)) {
                    listener.onEvent(event);
                }
            }

            @Override
            public void onReconnecting(Reconnect reconnect) {
                listener.onReconnecting(reconnect);
            }

            @Override
            public void onReconnected() {
                listener.onReconnected();
            }
        };
    }
}
