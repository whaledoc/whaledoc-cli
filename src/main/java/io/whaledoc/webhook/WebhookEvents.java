package io.whaledoc.webhook;

/**
 * The CLI keeps no list of event types: WhaleDoc's API knows them, so a new type works without updating the CLI.
 * {@code whaledoc trigger} sends any name and shows the API's answer for an unknown one, and {@code whaledoc listen}
 * receives every type unless {@code --events} names some.
 */
public final class WebhookEvents {

    public static final String ALL = "*";

    private WebhookEvents() {
    }
}
