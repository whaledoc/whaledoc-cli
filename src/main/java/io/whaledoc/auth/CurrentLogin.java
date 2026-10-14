package io.whaledoc.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Optional;

/**
 * A saved login that WhaleDoc still accepts: the account it belongs to, the computer it was made on, and the workspace
 * it works in, when it has one it may still use.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentLogin(Account account, String deviceName, Workspace workspace) {

    public Optional<Workspace> currentWorkspace() {
        return Optional.ofNullable(workspace);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(String email, String fullName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Workspace(String slug, String name) {
    }
}
