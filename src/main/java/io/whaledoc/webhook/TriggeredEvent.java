package io.whaledoc.webhook;

import io.whaledoc.console.TerminalText;

/** A test event the API sent to the workspace's event stream. */
public record TriggeredEvent(String id, String type) {

    public TriggeredEvent {

        id = TerminalText.clean(id);
        type = TerminalText.clean(type);
    }
}
