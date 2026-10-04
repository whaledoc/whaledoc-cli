package io.whaledoc.auth;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;

public record AuthSession(String sessionId, URI authorizationUrl, String authCode) {

    public AuthSession {

        if (StringUtils.isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }

        if (authorizationUrl == null) {
            throw new IllegalArgumentException("authorizationUrl must not be null");
        }

        if (!authorizationUrl.isAbsolute()) {
            throw new IllegalArgumentException("authorizationUrl must be absolute");
        }

        if (StringUtils.isBlank(authCode)) {
            throw new IllegalArgumentException("authCode must not be blank");
        }
    }
}