package io.whaledoc.commands.webhook;

import io.whaledoc.console.Console;
import io.whaledoc.http.SseEvent;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Prints one line per webhook event, so a stream of events is easy to scan:
 * a dim timestamp, the event type colored by outcome, and the local endpoint's response when forwarding.
 */
@RequiredArgsConstructor
final class EventPrinter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int EVENT_COLUMN_WIDTH = 20;

    private final Console console;
    private final Clock clock;

    void printEvent(SseEvent event) {
        console.println(eventLine(event));
        console.println(event.data());
        console.println();
    }

    void printForwarded(SseEvent event, int status) {
        console.println(eventLine(event) + " " + statusLabel(status));
    }

    void printForwardFailed(SseEvent event, String reason) {
        console.println(eventLine(event) + " " + console.red("failed") + " " + console.dim(reason));
    }

    private String eventLine(SseEvent event) {

        String timestamp = LocalDateTime.now(clock).format(TIMESTAMP_FORMAT);
        String type = "%-" + EVENT_COLUMN_WIDTH + "s";

        return "%s  %s %s".formatted(console.dim(timestamp), console.arrow(), colorByOutcome(type.formatted(event.event())));
    }

    private String colorByOutcome(String eventType) {

        String type = eventType.strip();

        if (type.endsWith(".failed")) {
            return console.red(eventType);
        }

        if (type.endsWith(".completed")) {
            return console.green(eventType);
        }

        return console.cyan(eventType);
    }

    private String statusLabel(int status) {

        String label = "[" + status + "]";

        return status < 300 ? console.green(label) : console.yellow(label);
    }
}
