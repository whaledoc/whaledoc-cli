package io.whaledoc.webhook;

/**
 * A message WhaleDoc sent on the {@code whaledoc listen} connection: its {@code type} ({@code ready}, {@code event},
 * {@code disconnected}, or one this version doesn't know), for an {@code event} the event's type and id, and the
 * whole message as received.
 */
public record ListenMessage(String type, String event, String id, String json) {

    public boolean isEvent() {
        return WebhookEvents.EVENT.equals(type);
    }
}
