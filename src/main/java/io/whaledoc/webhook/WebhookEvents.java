package io.whaledoc.webhook;

import java.util.List;

public final class WebhookEvents {

    public static final String ALL = "*";

    /** Every webhook event type, in the order help texts list them. */
    public static final List<String> ALLOWED = List.of(
            "job.completed",
            "job.failed",
            "job.cancelled",
            "usage.document_limit_reached",
            "usage.document_spending_cap_reached",
            "template.published",
            "template.deleted"
    );

    private WebhookEvents() {
    }
}
