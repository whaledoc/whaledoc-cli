package io.whaledoc.http;

import io.whaledoc.exceptions.ApiException;

import java.time.Duration;

/**
 * An upcoming reconnect attempt after an open event stream dropped.
 *
 * @param attempt     the number of this attempt, starting at 1
 * @param maxAttempts after how many failed attempts the client gives up
 * @param delay       how long the client waits before this attempt
 * @param cause       why the stream dropped, or null when the server closed it normally
 */
public record Reconnect(int attempt, int maxAttempts, Duration delay, ApiException cause) {
}
