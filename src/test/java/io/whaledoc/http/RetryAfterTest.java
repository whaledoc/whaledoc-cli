package io.whaledoc.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RetryAfterTest {

    private static final Instant NOW = Instant.parse("2026-10-14T09:30:00Z");

    @Test
    void shouldReadSecondsWhenHeaderIsANumber() {

        // when
        Optional<Duration> wait = RetryAfter.parse(Optional.of("42"), NOW);

        // then
        assertThat(wait).contains(Duration.ofSeconds(42));
    }

    @Test
    void shouldReadTimeUntilDateWhenHeaderIsADate() {

        // when
        Optional<Duration> wait = RetryAfter.parse(Optional.of("Wed, 14 Oct 2026 09:31:30 GMT"), NOW);

        // then
        assertThat(wait).contains(Duration.ofSeconds(90));
    }

    @Test
    void shouldWaitNothingWhenDateHasPassed() {

        // when
        Optional<Duration> wait = RetryAfter.parse(Optional.of("Wed, 14 Oct 2026 09:00:00 GMT"), NOW);

        // then
        assertThat(wait).contains(Duration.ZERO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "soon", "-5", "1.5"})
    void shouldIgnoreHeaderWhenItIsNotSecondsOrADate(String header) {

        // when
        Optional<Duration> wait = RetryAfter.parse(Optional.of(header), NOW);

        // then
        assertThat(wait).isEmpty();
    }

    @Test
    void shouldReturnNothingWhenHeaderIsMissing() {

        // when
        Optional<Duration> wait = RetryAfter.parse(Optional.empty(), NOW);

        // then
        assertThat(wait).isEmpty();
    }
}
