package io.whaledoc.config;

import lombok.Builder;

/**
 * The CLI's settings on this computer. {@code webhookSecret} signs the events {@code whaledoc listen} forwards; it is
 * made the first time it is needed and stays the same across logins, so a local endpoint keeps verifying them.
 */
@Builder(toBuilder = true)
public record WhaleDocConfig(String cliId, String accessToken, String webhookSecret) {
}
