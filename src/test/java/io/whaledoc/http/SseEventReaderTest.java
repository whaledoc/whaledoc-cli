package io.whaledoc.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventReaderTest {

    private final List<SseEvent> receivedEvents = new ArrayList<>();
    private final SseEventReader reader = new SseEventReader(receivedEvents::add);

    @Test
    void shouldJoinDataLinesWhenEventHasSeveralDataLines() throws IOException {

        // given
        InputStream stream = createStream("""
                event: document.created
                data: first line
                data: second line

                """);
        SseEvent expectedEvent = new SseEvent("document.created", "first line\nsecond line", null);

        // when
        reader.read(stream, () -> true);

        // then
        assertThat(receivedEvents).usingRecursiveFieldByFieldElementComparator().containsExactly(expectedEvent);
    }

    @Test
    void shouldIgnoreCommentsAndEventsWithoutDataWhenStreamHasKeepAlives() throws IOException {

        // given
        InputStream stream = createStream("""
                : keep-alive

                event: ping

                """);

        // when
        reader.read(stream, () -> true);

        // then
        assertThat(receivedEvents).isEmpty();
    }

    @Test
    void shouldRememberLastEventIdWhenEventsHaveIds() throws IOException {

        // given
        InputStream stream = createStream("""
                id: 41
                data: {}

                id: 42
                data: {}

                data: event without id

                """);

        // when
        reader.read(stream, () -> true);

        // then
        assertThat(reader.lastEventId()).isEqualTo("42");
    }

    private InputStream createStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
