package io.whaledoc.console;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalCapabilitiesTest {

    @Test
    void shouldSupportColorsAndUnicodeWhenRunningInMacosTerminal() {

        // given
        Map<String, String> env = Map.of("TERM", "xterm-256color", "COLORTERM", "truecolor");
        TerminalCapabilities expected = new TerminalCapabilities(true, true, true, true);

        // when
        TerminalCapabilities actual = TerminalCapabilities.detect(env, true, "Mac OS X", "UTF-8");

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldDisableColorsWhenOutputIsRedirected() {

        // given
        Map<String, String> env = Map.of("TERM", "xterm-256color");
        TerminalCapabilities expected = new TerminalCapabilities(false, false, false, true);

        // when
        TerminalCapabilities actual = TerminalCapabilities.detect(env, false, "Linux", "UTF-8");

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldDisableColorsWhenNoColorIsSet() {

        // given
        Map<String, String> env = Map.of("TERM", "xterm-256color", "NO_COLOR", "1");

        // when
        TerminalCapabilities actual = TerminalCapabilities.detect(env, true, "Linux", "UTF-8");

        // then
        assertThat(actual.colors()).isFalse();
    }

    @Test
    void shouldEnableColorsWhenForceColorIsSetForRedirectedOutput() {

        // given
        Map<String, String> env = Map.of("FORCE_COLOR", "1");

        // when
        TerminalCapabilities actual = TerminalCapabilities.detect(env, false, "Linux", "UTF-8");

        // then
        assertThat(actual.colors()).isTrue();
    }

    @Test
    void shouldDisableColorsAndUnicodeWhenRunningInClassicWindowsConsole() {

        // given
        Map<String, String> env = Map.of();
        TerminalCapabilities expected = new TerminalCapabilities(true, false, false, false);

        // when
        TerminalCapabilities actual = TerminalCapabilities.detect(env, true, "Windows 11", "Cp850");

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldSupportTrueColorWhenRunningInWindowsTerminal() {

        // given
        Map<String, String> env = Map.of("WT_SESSION", "5a1c3e2b-0000-0000-0000-000000000000");
        TerminalCapabilities expected = new TerminalCapabilities(true, true, true, false);

        // when
        TerminalCapabilities actual = TerminalCapabilities.detect(env, true, "Windows 11", "Cp850");

        // then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
}
