package io.whaledoc.webhook;

import java.util.Set;

/**
 * The CLI keeps no list of event types: WhaleDoc's API knows them, so a new type works without updating the CLI.
 * {@code whaledoc trigger} sends any name and shows the API's answer for an unknown one, and {@code whaledoc listen}
 * receives every type unless {@code --events} names some.
 */
public final class WebhookEvents {

    public static final String ALL = "*";
    /** The stream's first message, with the signing secret to show. */
    public static final String READY = "ready";
    /** The stream's last message when a teammate disconnected this listener from the WhaleDoc app. */
    public static final String DISCONNECTED = "disconnected";
    /** Messages about the stream itself, which {@code --events} never filters out. */
    public static final Set<String> STREAM_MESSAGES = Set.of(READY, DISCONNECTED);

    private WebhookEvents() {
    }
}
