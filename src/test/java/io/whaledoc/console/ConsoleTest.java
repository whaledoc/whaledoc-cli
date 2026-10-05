package io.whaledoc.console;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleTest {

    private static final TerminalCapabilities TRUE_COLOR_TERMINAL = new TerminalCapabilities(true, true, true, true);
    private static final TerminalCapabilities BASIC_COLOR_TERMINAL = new TerminalCapabilities(true, true, false, true);

    @Test
    void shouldWriteErrorsToStandardErrorWhenErrorIsReported() {

        // given
        TestConsole testConsole = TestConsole.plain();

        // when
        testConsole.console().error("Login failed");

        // then
        assertThat(testConsole.errors()).isEqualToIgnoringNewLines("x Login failed");
        assertThat(testConsole.output()).isEmpty();
    }

    @Test
    void shouldUseCheckMarkWhenTerminalSupportsUnicode() {

        // given
        TestConsole testConsole = TestConsole.withCapabilities(new TerminalCapabilities(true, false, false, true));

        // when
        testConsole.console().success("Logged in");

        // then
        assertThat(testConsole.output()).isEqualToIgnoringNewLines("✓ Logged in");
    }

    @Test
    void shouldNotAddEscapeCodesWhenColorsAreDisabled() {

        // given
        Console console = TestConsole.plain().console();

        // when
        String actual = console.bold(console.brand("Ready!"));

        // then
        assertThat(actual).isEqualTo("Ready!");
    }

    @Test
    void shouldUseExactBrandColorWhenTerminalSupportsTrueColor() {

        // given
        Console console = TestConsole.withCapabilities(TRUE_COLOR_TERMINAL).console();

        // when
        String actual = console.brand("Ready!");

        // then
        assertThat(actual).isEqualTo("\u001B[38;2;251;170;83mReady!\u001B[0m");
    }

    @Test
    void shouldUseClosestPaletteColorWhenTerminalSupportsOnlyBasicColors() {

        // given
        Console console = TestConsole.withCapabilities(BASIC_COLOR_TERMINAL).console();

        // when
        String actual = console.brand("Ready!");

        // then
        assertThat(actual).isEqualTo("\u001B[38;5;215mReady!\u001B[0m");
    }

    @Test
    void shouldPrintMessageOnceWhenSpinnerRunsOutsideTerminal() {

        // given
        TestConsole testConsole = TestConsole.plain();
        Spinner spinner = testConsole.console().spinner("Logging out...");

        // when
        spinner.start();
        spinner.stop();

        // then
        assertThat(testConsole.output()).isEqualToIgnoringNewLines("Logging out...");
    }
}
