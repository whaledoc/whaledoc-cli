package io.whaledoc.http;

public final class ApiConstants {

    public static final String API_VERSION = "v1";

    // auth
    public static final String AUTH_LOGIN = "/cli/auth/login";
    public static final String AUTH_LOGIN_EVENT = "/cli/auth/login/%s";
    public static final String AUTH_LOGOUT = "/cli/auth/logout";

    // webhook listener
    public static final String WEBHOOK_EVENTS = "/cli/webhooks/events";

    private ApiConstants() {
    }
}
