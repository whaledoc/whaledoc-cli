package io.whaledoc.console;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the terminal the CLI writes to can display.
 *
 * @param interactive whether output goes to a terminal rather than a file or pipe; spinners only animate there
 * @param colors      whether ANSI colors are shown
 * @param trueColor   whether 24-bit colors are shown, so the exact brand color can be used
 * @param unicode     whether characters such as ✓ and → can be printed
 */
public record TerminalCapabilities(boolean interactive, boolean colors, boolean trueColor, boolean unicode) {

    public static final TerminalCapabilities PLAIN = new TerminalCapabilities(false, false, false, false);

    private static final Set<String> TRUE_COLOR_VALUES = Set.of("truecolor", "24bit");

    public static TerminalCapabilities detect() {

        java.io.Console console = System.console();

        return detect(
                System.getenv(),
                console != null && console.isTerminal(),
                System.getProperty("os.name"),
                System.getProperty("stdout.encoding")
        );
    }

    static TerminalCapabilities detect(Map<String, String> env, boolean terminal, String osName, String outputEncoding) {

        boolean windows = osName.toLowerCase(Locale.ROOT).contains("win");
        boolean colors = supportsColors(env, terminal, windows);

        return new TerminalCapabilities(
                terminal,
                colors,
                colors && supportsTrueColor(env),
                supportsUnicode(outputEncoding, windows)
        );
    }

    // Follows https://no-color.org and https://force-color.org
    private static boolean supportsColors(Map<String, String> env, boolean terminal, boolean windows) {

        if (isSet(env, "NO_COLOR")) {
            return false;
        }

        if (isSet(env, "FORCE_COLOR")) {
            return true;
        }

        if (!terminal || "dumb".equals(env.get("TERM"))) {
            return false;
        }

        // The classic Windows console prints escape codes as text; Windows Terminal, VS Code and ConEmu render them
        if (windows) {
            return isSet(env, "WT_SESSION") || isSet(env, "TERM_PROGRAM") || isSet(env, "TERM")
                    || "ON".equalsIgnoreCase(env.get("ConEmuANSI"));
        }

        return true;
    }

    private static boolean supportsTrueColor(Map<String, String> env) {

        String colorTerm = env.getOrDefault("COLORTERM", "").toLowerCase(Locale.ROOT);

        return TRUE_COLOR_VALUES.contains(colorTerm) || isSet(env, "WT_SESSION");
    }

    // Windows consoles usually use a legacy code page in which ✓ and → print as "?"
    private static boolean supportsUnicode(String outputEncoding, boolean windows) {

        if (outputEncoding == null) {
            return !windows;
        }

        String encoding = outputEncoding.toLowerCase(Locale.ROOT).replace("-", "");

        return encoding.equals("utf8") || encoding.equals("cp65001");
    }

    private static boolean isSet(Map<String, String> env, String name) {

        String value = env.get(name);

        return value != null && !value.isEmpty();
    }
}
