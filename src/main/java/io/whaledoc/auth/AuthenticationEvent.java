package io.whaledoc.auth;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * An approved login: the access token, and the account's email and the workspace the user chose, when the API sends
 * them.
 */
public record AuthenticationEvent(String accessToken, String accountEmail, String workspaceName) {

    public AuthenticationEvent {

        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
    }

    public Optional<String> account() {
        return Optional.ofNullable(accountEmail).filter(StringUtils::isNotBlank);
    }

    public Optional<String> workspace() {
        return Optional.ofNullable(workspaceName).filter(StringUtils::isNotBlank);
    }
}
