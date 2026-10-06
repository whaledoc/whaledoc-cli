package io.whaledoc.config;

import io.whaledoc.exceptions.ConfigException;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Where the CLI keeps its files, following each operating system's conventions:
 *
 * <table>
 *     <tr><th></th><th>Settings and login</th><th>Logs</th></tr>
 *     <tr><td>Windows</td><td>%APPDATA%\WhaleDoc</td><td>%LOCALAPPDATA%\WhaleDoc\logs</td></tr>
 *     <tr><td>macOS</td><td>~/.config/whaledoc</td><td>~/Library/Logs/WhaleDoc</td></tr>
 *     <tr><td>Linux</td><td>$XDG_CONFIG_HOME/whaledoc (~/.config/whaledoc)</td><td>$XDG_STATE_HOME/whaledoc/logs (~/.local/state/whaledoc/logs)</td></tr>
 * </table>
 *
 * @param config the directory for the settings and the login
 * @param logs   the directory for log files
 */
public record AppDirectories(Path config, Path logs) {

    // Windows and macOS show folder names to users, so they get the product name; Linux uses lowercase names
    // Windows and macOS show these folders to users, so they use the product name; Linux uses lowercase
    private static final String DISPLAY_NAME = "WhaleDoc";
    private static final String DIRECTORY_NAME = "whaledoc";

    public static AppDirectories forCurrentUser() {
        return forPlatform(System.getProperty("os.name"), System.getenv(), System.getProperty("user.home"));
    }

    static AppDirectories forPlatform(String osName, Map<String, String> env, String userHome) {

        String os = osName.toLowerCase(Locale.ROOT);
        Path home = Path.of(userHome);

        if (os.contains("win")) {
            return forWindows(env, home);
        }

        if (os.contains("mac")) {
            return new AppDirectories(
                    home.resolve(".config").resolve(DIRECTORY_NAME),
                    home.resolve("Library").resolve("Logs").resolve(DISPLAY_NAME)
            );
        }

        if (os.contains("linux")) {
            return new AppDirectories(
                    directoryFromEnv(env, "XDG_CONFIG_HOME", home.resolve(".config")).resolve(DIRECTORY_NAME),
                    directoryFromEnv(env, "XDG_STATE_HOME", home.resolve(".local").resolve("state")).resolve(DIRECTORY_NAME).resolve("logs")
            );
        }

        throw new ConfigException("Unsupported operating system: " + osName);
    }

    // Settings roam with the user's profile; logs are machine-specific, so they belong in the local app data
    private static AppDirectories forWindows(Map<String, String> env, Path home) {

        String appData = env.get("APPDATA");

        if (appData == null || appData.isBlank()) {
            throw new ConfigException("APPDATA environment variable is not available.");
        }

        Path localAppData = directoryFromEnv(env, "LOCALAPPDATA", home.resolve("AppData").resolve("Local"));

        return new AppDirectories(
                Path.of(appData, DISPLAY_NAME),
                localAppData.resolve(DISPLAY_NAME).resolve("logs")
        );
    }

    private static Path directoryFromEnv(Map<String, String> env, String variable, Path fallback) {

        String value = env.get(variable);

        return value == null || value.isBlank() ? fallback : Path.of(value);
    }
}
