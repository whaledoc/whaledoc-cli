package io.whaledoc.webhook;

import java.util.Map;

/**
 * A webhook event as WhaleDoc sends it to {@code whaledoc listen}: the request a real delivery makes, with its signed
 * {@code headers} and the exact {@code body}. The CLI forwards both as they are and signs nothing.
 */
public record SignedEvent(String id, String type, Map<String, String> headers, String body) {
}
