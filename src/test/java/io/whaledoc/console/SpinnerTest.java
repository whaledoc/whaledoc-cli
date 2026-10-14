package io.whaledoc.console;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpinnerTest {

    private static final TerminalCapabilities INTERACTIVE = new TerminalCapabilities(true, false, false, false);
    private static final String BACK_TO_SPINNER_LINE = "\u001B[1A\r\u001B[2K";

    @Test
    void shouldGoBackToItsLineWhenEnterWasEchoedWhileSpinning() {

        // given
        TestConsole testConsole = TestConsole.withCapabilities(INTERACTIVE);
        Spinner spinner = testConsole.console().spinner("Waiting...");
        spinner.start();

        // when
        spinner.returnAfterEnter();
        spinner.stop();

        // then
        assertThat(testConsole.errors()).contains(BACK_TO_SPINNER_LINE);
    }

    @Test
    void shouldNotMoveCursorWhenEnterComesAfterSpinnerStopped() {

        // given
        TestConsole testConsole = TestConsole.withCapabilities(INTERACTIVE);
        Spinner spinner = testConsole.console().spinner("Waiting...");
        spinner.start();
        spinner.stop();

        // when
        spinner.returnAfterEnter();

        // then
        assertThat(testConsole.errors()).doesNotContain(BACK_TO_SPINNER_LINE);
    }
}
