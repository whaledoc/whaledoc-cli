package io.whaledoc.http;

/**
 * Paths of the WhaleDoc API used by the CLI.
 */
public final class ApiConstants {

    public static final String API_VERSION_HEADER = "API-Version";
    public static final String API_VERSION = "1";

    // auth
    public static final String AUTH_SESSIONS = "/cli/auth/sessions";
    public static final String AUTH_SESSION_EVENTS = "/cli/auth/sessions/%s/events";
    public static final String AUTH_LOGOUT = "/cli/auth/logout";

    // webhook listener
    public static final String WEBHOOK_EVENTS = "/cli/webhooks/events";
    public static final String WEBHOOK_TEST_EVENTS = "/cli/webhooks/test-events";

    // workspaces
    public static final String WORKSPACES = "/cli/workspaces";
    public static final String CURRENT_WORKSPACE = "/cli/workspaces/current";

    private ApiConstants() {
    }
}
