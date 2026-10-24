package io.whaledoc.webhook;

/** The headers a forwarded event carries: those of a real webhook delivery, as Standard Webhooks names them. */
public final class ForwardHeaders {

    public static final String CONTENT_TYPE = "Content-Type";
    public static final String WEBHOOK_ID = "webhook-id";
    public static final String WEBHOOK_TIMESTAMP = "webhook-timestamp";
    public static final String WEBHOOK_SIGNATURE = "webhook-signature";
    public static final String EVENT = "WhaleDoc-Event";

    private ForwardHeaders() {
    }
}
