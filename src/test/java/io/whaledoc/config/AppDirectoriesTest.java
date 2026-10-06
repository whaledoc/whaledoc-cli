package io.whaledoc.config;

import io.whaledoc.exceptions.ConfigException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class AppDirectoriesTest {

    private static final String WINDOWS_HOME = "C:\\Users\\jane";
    private static final String UNIX_HOME = "/home/jane";

    @Test
    void shouldUseRoamingSettingsAndLocalLogsWhenRunningOnWindows() {

        // given
        Map<String, String> env = Map.of(
                "APPDATA", "C:\\Users\\jane\\AppData\\Roaming",
                "LOCALAPPDATA", "C:\\Users\\jane\\AppData\\Local"
        );
        AppDirectories expected = new AppDirectories(
                Path.of("C:\\Users\\jane\\AppData\\Roaming", "WhaleDoc"),
                Path.of("C:\\Users\\jane\\AppData\\Local", "WhaleDoc", "logs")
        );

        // when
        AppDirectories actual = AppDirectories.forPlatform("Windows 11", env, WINDOWS_HOME);

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldFallBackToDefaultLocalAppDataWhenVariableIsMissingOnWindows() {

        // given
        Map<String, String> env = Map.of("APPDATA", "C:\\Users\\jane\\AppData\\Roaming");

        // when
        AppDirectories actual = AppDirectories.forPlatform("Windows 11", env, WINDOWS_HOME);

        // then
        assertThat(actual.logs()).isEqualTo(Path.of(WINDOWS_HOME, "AppData", "Local", "WhaleDoc", "logs"));
    }

    @Test
    void shouldUseLibraryLogsWhenRunningOnMacos() {

        // given
        AppDirectories expected = new AppDirectories(
                Path.of(UNIX_HOME, ".config", "whaledoc"),
                Path.of(UNIX_HOME, "Library", "Logs", "WhaleDoc")
        );

        // when
        AppDirectories actual = AppDirectories.forPlatform("Mac OS X", Map.of(), UNIX_HOME);

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldUseXdgDefaultsWhenRunningOnLinuxWithoutXdgVariables() {

        // given
        AppDirectories expected = new AppDirectories(
                Path.of(UNIX_HOME, ".config", "whaledoc"),
                Path.of(UNIX_HOME, ".local", "state", "whaledoc", "logs")
        );

        // when
        AppDirectories actual = AppDirectories.forPlatform("Linux", Map.of(), UNIX_HOME);

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldUseXdgVariablesWhenTheyAreSetOnLinux() {

        // given
        Map<String, String> env = Map.of("XDG_CONFIG_HOME", "/data/config", "XDG_STATE_HOME", "/data/state");
        AppDirectories expected = new AppDirectories(
                Path.of("/data/config", "whaledoc"),
                Path.of("/data/state", "whaledoc", "logs")
        );

        // when
        AppDirectories actual = AppDirectories.forPlatform("Linux", env, UNIX_HOME);

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldThrowConfigExceptionWhenOperatingSystemIsUnsupported() {

        // given
        String osName = "FreeBSD";

        // when
        Throwable thrown = catchThrowable(() -> AppDirectories.forPlatform(osName, Map.of(), UNIX_HOME));

        // then
        assertThat(thrown)
                .isInstanceOf(ConfigException.class)
                .hasMessage("Unsupported operating system: FreeBSD");
    }
}
