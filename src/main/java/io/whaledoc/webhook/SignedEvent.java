package io.whaledoc.webhook;

import io.whaledoc.console.TerminalText;

import java.util.Map;

/**
 * A webhook event as WhaleDoc sends it to {@code whaledoc listen}: the request a real delivery makes, with its signed
 * {@code headers} and the exact {@code body}. The CLI forwards both as they are and signs nothing. The id and type are
 * printed: they hold no terminal control characters.
 */
public record SignedEvent(String id, String type, Map<String, String> headers, String body) {

    public SignedEvent {

        id = TerminalText.clean(id);
        type = TerminalText.clean(type);
    }
}
