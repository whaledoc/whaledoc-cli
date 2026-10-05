package io.whaledoc.auth;

import org.apache.commons.lang3.StringUtils;

public record AuthenticationEvent(String accessToken) {

    public AuthenticationEvent {

        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
    }
}