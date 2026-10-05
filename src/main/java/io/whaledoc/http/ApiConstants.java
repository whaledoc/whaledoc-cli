package io.whaledoc.http;

/**
 * Paths of the WhaleDoc API used by the CLI. The full contract is described in docs/cli-api.md.
 */
public final class ApiConstants {

    public static final String API_VERSION = "v1";

    // auth
    public static final String AUTH_SESSIONS = "/cli/auth/sessions";
    public static final String AUTH_SESSION_EVENTS = "/cli/auth/sessions/%s/events";
    public static final String AUTH_LOGOUT = "/cli/auth/logout";

    // webhook listener
    public static final String WEBHOOK_EVENTS = "/cli/webhooks/events";

    private ApiConstants() {
    }
}
