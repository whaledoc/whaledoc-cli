package io.whaledoc.http;

import io.whaledoc.exceptions.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        ApiException exception = ErrorResponses.toException(429, body);

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
        ApiException exception = ErrorResponses.toException(404, body);

        // then
        assertThat(exception).hasMessage("Not Found");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Something went wrong", "[1, 2]", "{\"detail\": 42}"})
    void shouldShowBodyAsSentWhenItIsNotProblemDetails(String body) {

        // when
        ApiException exception = ErrorResponses.toException(500, body);

        // then
        assertThat(exception).hasMessage("API request failed with status 500: " + body);
    }

    @Test
    void shouldNameOnlyTheStatusWhenBodyIsEmpty() {

        // when
        ApiException exception = ErrorResponses.toException(502, "");

        // then
        assertThat(exception).hasMessage("API request failed with status 502.");
    }
}
