package io.whaledoc.webhook;

import java.util.Set;

public final class WebhookEvents {

    public static final String ALL = "*";

    public static final Set<String> ALLOWED = Set.of(
            "document.created",
            "document.completed",
            "document.failed"
    );

    private WebhookEvents() {
    }
}
