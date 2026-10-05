package io.whaledoc.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Parses a {@code text/event-stream} into events and remembers the ID of the last one,
 * so a reconnect can ask the server to continue from there.
 */
final class SseEventReader {

    private final Consumer<SseEvent> eventConsumer;

    private String lastEventId;
    private String event;
    private String id;
    private final StringBuilder data = new StringBuilder();

    SseEventReader(Consumer<SseEvent> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    void read(InputStream inputStream, BooleanSupplier isOpen) throws IOException {

        resetEvent();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;

            while (isOpen.getAsBoolean() && (line = reader.readLine()) != null) {
                readLine(line);
            }
        }
    }

    String lastEventId() {
        return lastEventId;
    }

    private void readLine(String line) {

        // An empty line terminates an event
        if (line.isEmpty()) {
            dispatchEvent();
            return;
        }

        // Comments are commonly used as keep-alive messages, e.g. ": keep-alive"
        if (line.startsWith(":")) {
            return;
        }

        String value = fieldValue(line);

        if (line.startsWith("event:")) {
            event = value;
        } else if (line.startsWith("id:")) {
            id = value;
        } else if (line.startsWith("data:")) {
            appendData(value);
        }

        // "retry:" is deliberately ignored: reconnecting uses the client's own backoff
    }

    private void dispatchEvent() {

        if (!data.isEmpty()) {

            if (id != null) {
                lastEventId = id;
            }

            eventConsumer.accept(new SseEvent(event, data.toString(), id));
        }

        resetEvent();
    }

    private void appendData(String value) {

        if (!data.isEmpty()) {
            data.append('\n');
        }

        data.append(value);
    }

    private void resetEvent() {
        event = null;
        id = null;
        data.setLength(0);
    }

    private static String fieldValue(String line) {

        String value = line.substring(line.indexOf(':') + 1);

        return value.startsWith(" ") ? value.substring(1) : value;
    }
}
