package io.whaledoc.http;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Reads a {@code Retry-After} header (RFC 9110): a number of seconds, or the date after which to try again.
 */
final class RetryAfter {

    private RetryAfter() {
    }

    static Optional<Duration> parse(Optional<String> header, Instant now) {

        return header
                .map(String::strip)
                .flatMap(value -> seconds(value).or(() -> untilDate(value, now)));
    }

    private static Optional<Duration> seconds(String value) {

        if (!value.chars().allMatch(Character::isDigit) || value.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(Duration.ofSeconds(Long.parseLong(value)));
    }

    private static Optional<Duration> untilDate(String value, Instant now) {

        try {
            Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration wait = Duration.between(now, retryAt);
            return Optional.of(wait.isNegative() ? Duration.ZERO : wait);

        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
