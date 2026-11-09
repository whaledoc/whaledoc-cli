package io.whaledoc.console;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Text received over the network, made safe to print: without the control characters a terminal acts on (escape
 * sequences that move the cursor, clear the screen, change the title or write links), so a name or a message can only
 * ever show as text. Tabs and line breaks stay.
 */
public final class TerminalText {

    // C0 controls except tab and line feed, DEL, and the C1 controls (U+009B starts an escape sequence on its own)
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\x00-\\x08\\x0B-\\x1F\\x7F-\\x9F]");

    private TerminalText() {
    }

    /** The text without terminal control characters; null stays null, as records read from JSON may hold it. */
    public static String clean(String text) {

        return Optional.ofNullable(text)
                .map(received -> CONTROL_CHARACTERS.matcher(received).replaceAll(""))
                .orElse(null);
    }
}
