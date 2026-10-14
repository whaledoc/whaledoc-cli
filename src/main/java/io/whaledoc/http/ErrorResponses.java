package io.whaledoc.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.whaledoc.exceptions.ApiException;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Optional;

/**
 * Turns an error response into the message the user sees. The API answers errors with RFC 9457 problem details,
 * whose {@code detail} (or else {@code title}) is a short, readable sentence; any other body is shown as sent.
 */
final class ErrorResponses {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private ErrorResponses() {
    }

    static ApiException toException(int statusCode, String body) {

        String message = problemMessage(body)
                .orElseGet(() -> plainMessage(statusCode, body));

        return new ApiException(statusCode, message);
    }

    private static Optional<String> problemMessage(String body) {

        return parse(body)
                .flatMap(problem -> textField(problem, "detail").or(() -> textField(problem, "title")));
    }

    private static Optional<JsonNode> parse(String body) {

        if (StringUtils.isBlank(body)) {
            return Optional.empty();
        }

        try {
            return Optional.of(OBJECT_MAPPER.readTree(body)).filter(JsonNode::isObject);

        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> textField(JsonNode problem, String name) {

        return Optional.ofNullable(problem.get(name))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .filter(StringUtils::isNotBlank);
    }

    private static String plainMessage(int statusCode, String body) {

        if (StringUtils.isBlank(body)) {
            return "API request failed with status " + statusCode + ".";
        }

        return "API request failed with status " + statusCode + ": " + body.strip();
    }
}
