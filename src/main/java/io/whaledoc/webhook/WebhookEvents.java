package io.whaledoc.webhook;

/**
 * The CLI keeps no list of event types: WhaleDoc's API knows them, so a new type works without updating the CLI.
 * {@code whaledoc trigger} sends any name and shows the API's answer for an unknown one, and {@code whaledoc listen}
 * receives every type unless {@code --events} names some.
 */
public final class WebhookEvents {

    public static final String ALL = "*";
    /** The connection's first message, with the signing secret to show. */
    public static final String READY = "ready";
    /** A webhook event; {@code --events} filters only these. */
    public static final String EVENT = "event";
    /** The connection's last message when a teammate disconnected this listener from the WhaleDoc app. */
    public static final String DISCONNECTED = "disconnected";
    /** What the CLI sends back after forwarding an event. */
    public static final String ANSWER = "answer";

    private WebhookEvents() {
    }
}
