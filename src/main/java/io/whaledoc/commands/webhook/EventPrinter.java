package io.whaledoc.commands.webhook;

import io.whaledoc.console.Console;
import io.whaledoc.webhook.ForwardResult;
import io.whaledoc.webhook.SignedEvent;
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

    void printEvent(SignedEvent event) {
        console.println(eventLine(event));
        console.println(event.body());
        console.println();
    }

    void printForwarded(SignedEvent event, ForwardResult result) {

        String outcome = result.status()
                .map(this::statusLabel)
                .orElseGet(() -> console.red("failed") + " " + console.dim(result.failure().orElseThrow().reason()));

        console.println(eventLine(event) + " " + outcome);
    }

    private String eventLine(SignedEvent event) {

        String timestamp = LocalDateTime.now(clock).format(TIMESTAMP_FORMAT);
        String type = "%-" + EVENT_COLUMN_WIDTH + "s";

        return "%s  %s %s".formatted(console.dim(timestamp), console.arrow(), colorByOutcome(type.formatted(event.type())));
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
