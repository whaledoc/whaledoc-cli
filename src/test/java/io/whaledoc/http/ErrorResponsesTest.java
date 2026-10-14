package io.whaledoc.http;

import io.whaledoc.exceptions.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponsesTest {

    @Test
    void shouldUseDetailWhenBodyIsProblemDetails() {

        // given
        String body = """
                {"type": "about:blank", "title": "Too Many Requests", "status": 429,
                 "detail": "Too many attempts. Try again in 5 minutes."}
                """;

        // when
        ApiException exception = ErrorResponses.toException(429, body, noHeaders());

        // then
        assertThat(exception).hasMessage("Too many attempts. Try again in 5 minutes.");
        assertThat(exception.statusCode()).isEqualTo(429);
    }

    @Test
    void shouldUseTitleWhenProblemDetailsHaveNoDetail() {

        // given
        String body = """
                {"title": "Not Found", "status": 404}
                """;

        // when
        ApiException exception = ErrorResponses.toException(404, body, noHeaders());

        // then
        assertThat(exception).hasMessage("Not Found");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Something went wrong", "[1, 2]", "{\"detail\": 42}"})
    void shouldShowBodyAsSentWhenItIsNotProblemDetails(String body) {

        // when
        ApiException exception = ErrorResponses.toException(500, body, noHeaders());

        // then
        assertThat(exception).hasMessage("API request failed with status 500: " + body);
    }

    @Test
    void shouldNameOnlyTheStatusWhenBodyIsEmpty() {

        // when
        ApiException exception = ErrorResponses.toException(502, "", noHeaders());

        // then
        assertThat(exception).hasMessage("API request failed with status 502.");
    }

    @Test
    void shouldKeepWaitWhenResponseSaysHowLongToWait() {

        // given
        HttpHeaders headers = HttpHeaders.of(Map.of("Retry-After", List.of("12")), (name, value) -> true);

        // when
        ApiException exception = ErrorResponses.toException(429, "", headers);

        // then
        assertThat(exception.retryAfter()).contains(Duration.ofSeconds(12));
    }

    private static HttpHeaders noHeaders() {
        return HttpHeaders.of(Map.of(), (name, value) -> true);
    }
}
