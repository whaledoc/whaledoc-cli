package io.whaledoc.utility;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Opens a URL in the user's default browser with the operating system's own command.
 *
 * <p>Java's desktop support (AWT) is unreliable in a native executable and adds to its size,
 * so it is deliberately not used.
 */
@Slf4j
public final class BrowserLauncher {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

    /**
     * @return whether the browser could be started
     */
    public boolean open(URI url) {

        // Only web URLs are handed to the OS, never files or other registered URL handlers
        if (!SUPPORTED_SCHEMES.contains(url.getScheme())) {
            return false;
        }

        try {
            new ProcessBuilder(openCommand(url.toString()))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            return true;

        } catch (IOException e) {
            log.debug("Unable to open {} in a browser", url, e);
            return false;
        }
    }

    private static List<String> openCommand(String url) {

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (os.contains("win")) {
            // Unlike "cmd /c start", this doesn't break on & in the URL
            return List.of("rundll32", "url.dll,FileProtocolHandler", url);
        }

        if (os.contains("mac")) {
            return List.of("open", url);
        }

        return List.of("xdg-open", url);
    }
}
