package io.whaledoc.http;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffTest {

    @Test
    void shouldAllowTwoQuickRetriesWhenNeverConnected() {

        // given
        Backoff backoff = new Backoff();

        // when
        List<Duration> actualDelays = collectDelays(backoff);

        // then
        assertThat(actualDelays).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Test
    void shouldRetryTenTimesWithExponentialBackoffWhenConnectionDropped() {

        // given
        Backoff backoff = new Backoff();
        backoff.connected();
        List<Duration> expectedDelays = createDelays(1, 2, 4, 8, 16, 30, 30, 30, 30, 30);

        // when
        List<Duration> actualDelays = collectDelays(backoff);

        // then
        assertThat(actualDelays).isEqualTo(expectedDelays);
    }

    @Test
    void shouldStartOverWhenReconnected() {

        // given
        Backoff backoff = new Backoff();
        backoff.connected();
        backoff.nextAttempt();
        backoff.nextAttempt();

        // when
        backoff.connected();
        backoff.nextAttempt();

        // then
        assertThat(backoff.attempt()).isEqualTo(1);
        assertThat(backoff.delay()).isEqualTo(Duration.ofSeconds(1));
    }

    private List<Duration> collectDelays(Backoff backoff) {

        List<Duration> delays = new ArrayList<>();

        while (backoff.nextAttempt()) {
            delays.add(backoff.delay());
        }

        return delays;
    }

    private List<Duration> createDelays(long... seconds) {

        List<Duration> delays = new ArrayList<>();

        for (long second : seconds) {
            delays.add(Duration.ofSeconds(second));
        }

        return delays;
    }
}
