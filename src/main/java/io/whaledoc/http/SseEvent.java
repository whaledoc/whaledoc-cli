package io.whaledoc.http;

public record SseEvent(
        String event,
        String data,
        String id
) {
}
