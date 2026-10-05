package io.whaledoc.console;

import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.Style;
import picocli.CommandLine.Help.ColorScheme;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * The terminal the commands talk to. All output goes through here so it looks the same everywhere:
 * results go to standard output, errors and warnings to standard error, and colors and symbols are
 * only used when the terminal can show them.
 */
public final class Console {

    private static final String RESET = "\u001B[0m";

    private final TerminalCapabilities capabilities;
    private final InputStream in;
    private final PrintStream out;
    private final PrintStream err;

    public Console(TerminalCapabilities capabilities, InputStream in, PrintStream out, PrintStream err) {
        this.capabilities = capabilities;
        this.in = in;
        this.out = out;
        this.err = err;
    }

    public static Console system() {
        return new Console(TerminalCapabilities.detect(), System.in, System.out, System.err);
    }

    public void println(String text) {
        out.println(text);
    }

    public void println() {
        out.println();
    }

    public void success(String message) {
        out.println(green(symbol("✓", "+")) + " " + message);
    }

    public void warning(String message) {
        err.println(yellow("!") + " " + message);
    }

    public void error(String message) {
        err.println(red(symbol("✗", "x")) + " " + message);
    }

    /**
     * Blocks until the user presses Enter.
     *
     * @return false when input has ended, e.g. when it isn't a terminal
     */
    public boolean waitForEnter() {

        try {
            return in.read() != -1;
        } catch (IOException e) {
            return false;
        }
    }

    public Spinner spinner(String message) {
        return new Spinner(this, message);
    }

    public String brand(String text) {

        String color = capabilities.trueColor() ? "38;2;" + Theme.BRAND_RGB : "38;5;" + Theme.BRAND_256;

        return style(text, color);
    }

    public String bold(String text) {
        return style(text, "1");
    }

    public String dim(String text) {
        return style(text, "2");
    }

    public String red(String text) {
        return style(text, "31");
    }

    public String green(String text) {
        return style(text, "32");
    }

    public String yellow(String text) {
        return style(text, "33");
    }

    public String cyan(String text) {
        return style(text, "36");
    }

    public String arrow() {
        return symbol("→", "->");
    }

    /**
     * Colors for picocli's help and error messages, matching the rest of the output.
     */
    public ColorScheme helpColorScheme() {

        String brand = "fg(" + Theme.BRAND_256 + ")";

        return new ColorScheme.Builder(capabilities.colors() ? Ansi.ON : Ansi.OFF)
                .commands(Style.parse("bold," + brand))
                .options(Style.parse(brand))
                .parameters(Style.italic)
                .optionParams(Style.italic)
                .errors(Style.fg_red, Style.bold)
                .stackTraces(Style.italic)
                .build();
    }

    boolean isInteractive() {
        return capabilities.interactive();
    }

    boolean supportsUnicode() {
        return capabilities.unicode();
    }

    private String symbol(String unicode, String ascii) {
        return capabilities.unicode() ? unicode : ascii;
    }

    void print(String text) {
        out.print(text);
        out.flush();
    }

    private String style(String text, String code) {

        if (!capabilities.colors()) {
            return text;
        }

        return "\u001B[" + code + "m" + text + RESET;
    }
}
