package io.whaledoc.console;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * A console without colors that records what is written to standard output and standard error.
 */
public final class TestConsole {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    private final Console console;

    private TestConsole(TerminalCapabilities capabilities, String userInput) {

        this.console = new Console(
                capabilities,
                new ByteArrayInputStream(userInput.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8)
        );
    }

    public static TestConsole plain() {
        return new TestConsole(TerminalCapabilities.PLAIN, "");
    }

    public static TestConsole withInput(String userInput) {
        return new TestConsole(TerminalCapabilities.PLAIN, userInput);
    }

    public static TestConsole withCapabilities(TerminalCapabilities capabilities) {
        return new TestConsole(capabilities, "");
    }

    public Console console() {
        return console;
    }

    public String output() {
        return output.toString(StandardCharsets.UTF_8);
    }

    public String errors() {
        return errors.toString(StandardCharsets.UTF_8);
    }
}
