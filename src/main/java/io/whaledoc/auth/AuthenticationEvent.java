package io.whaledoc.auth;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/** An approved login: the access token, and the name of the workspace the user chose, when the API sends it. */
public record AuthenticationEvent(String accessToken, String workspaceName) {

    public AuthenticationEvent {

        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
    }

    public Optional<String> workspace() {
        return Optional.ofNullable(workspaceName).filter(StringUtils::isNotBlank);
    }
}