package io.whaledoc.console;

import io.whaledoc.webhook.SignedEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalTextTest {

    private static final String ESC = "\u001b";

    @Test
    void shouldRemoveEscapeSequencesWhenTextHoldsThem() {

        // given
        String text = "Acme" + ESC + "[2J" + ESC + "]0;pwned\u0007 HR";

        // when
        String cleaned = TerminalText.clean(text);

        // then
        assertThat(cleaned).isEqualTo("Acme[2J]0;pwned HR");
    }

    @Test
    void shouldRemoveCarriageReturnAndSingleCharacterEscapeWhenTextHoldsThem() {

        // given
        String text = "Logged in\rFake line\u009b31m";

        // when
        String cleaned = TerminalText.clean(text);

        // then
        assertThat(cleaned).isEqualTo("Logged inFake line31m");
    }

    @Test
    void shouldKeepTabsLineBreaksAndUnicodeWhenTextHoldsThem() {

        // given
        String text = "Zoë\tMüller\nAcme ✓";

        // when
        String cleaned = TerminalText.clean(text);

        // then
        assertThat(cleaned).isEqualTo(text);
    }

    @Test
    void shouldKeepNullWhenThereIsNoText() {

        // when
        String cleaned = TerminalText.clean(null);

        // then
        assertThat(cleaned).isNull();
    }

    @Test
    void shouldCleanPrintedFieldsAndKeepBodyWhenEventHoldsControlCharacters() {

        // given
        String body = "{\"name\": \"" + ESC + "[31m\"}";

        // when
        SignedEvent event = new SignedEvent("evt_1" + ESC + "[2J", "template.published" + ESC, Map.of(), body);

        // then
        assertThat(event.id()).isEqualTo("evt_1[2J");
        assertThat(event.type()).isEqualTo("template.published");
        assertThat(event.body()).isEqualTo(body);
    }
}
