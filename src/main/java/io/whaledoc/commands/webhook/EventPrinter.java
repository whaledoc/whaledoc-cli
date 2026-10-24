package io.whaledoc.commands.webhook;

import io.whaledoc.console.Console;
import io.whaledoc.webhook.ForwardResult;
import io.whaledoc.webhook.SignedEvent;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Prints a line per webhook event: a dim timestamp, the event type, which opens the
 * event in the WhaleDoc app when clicked, and its id. When forwarding, a second line gives the local endpoint's
 * answer.
 */
@RequiredArgsConstructor
final class EventPrinter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** The event on the app's Webhooks page, in its Events tab. */
    private static final String EVENT_PATH = "/settings/webhooks?tab=events&event=";
    private static final String POST = "POST";

    private final Console console;
    private final Clock clock;
    private final String appUrl;

    /** {@code 2026-10-24 22:56:02  -> template.published [evt_…]} */
    void printEvent(SignedEvent event) {

        String type = console.link(colorByOutcome(event.type()), eventUrl(event));
        console.println("%s  %s %s %s".formatted(timestamp(), console.arrow(), type, console.dim(idLabel(event))));
    }

    /** {@code 2026-10-24 22:56:02  <- [200] POST http://localhost:3000/webhooks [evt_…]}, or why it got no answer. */
    void printForwarded(URI target, SignedEvent event, ForwardResult result) {

        String outcome = switch (result) {
            case ForwardResult.Answered answered -> "%s %s %s".formatted(statusLabel(answered.status()), POST, target);
            case ForwardResult.Unanswered unanswered -> console.red("failed") + " " + console.dim(unanswered.reason());
        };

        console.println("%s  %s %s %s".formatted(timestamp(), console.backArrow(), outcome, console.dim(idLabel(event))));
    }

    private String timestamp() {

        return console.dim(LocalDateTime.now(clock).format(TIMESTAMP_FORMAT));
    }

    private String eventUrl(SignedEvent event) {

        return appUrl + EVENT_PATH + URLEncoder.encode(String.valueOf(event.id()), StandardCharsets.UTF_8);
    }

    private static String idLabel(SignedEvent event) {

        return "[" + event.id() + "]";
    }

    private String colorByOutcome(String type) {

        if (type.endsWith(".failed")) {
            return console.red(type);
        }

        if (type.endsWith(".completed")) {
            return console.green(type);
        }

        return console.cyan(type);
    }

    private String statusLabel(int status) {

        String label = "[" + status + "]";

        return status < 300 ? console.green(label) : console.yellow(label);
    }
}
