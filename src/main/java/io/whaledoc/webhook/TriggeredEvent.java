package io.whaledoc.webhook;

/** A test event the API sent to the workspace's event stream. */
public record TriggeredEvent(String id, String type) {
}
