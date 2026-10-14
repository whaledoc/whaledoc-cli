package io.whaledoc.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.http.*;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public final class AuthClient {

    private static final String AUTHENTICATED_EVENT = "authenticated";

    private static final Duration AUTHENTICATION_TIMEOUT = Duration.ofMinutes(5);

    private final ApiClient apiClient;
    private final SseClient sseClient;
    private final ObjectMapper objectMapper;

    public AuthClient(ApiClient apiClient, SseClient sseClient, ObjectMapper objectMapper) {

        this.apiClient = apiClient;
        this.sseClient = sseClient;
        this.objectMapper = objectMapper;
    }

    /** Starts a login for this installation, named after the computer it runs on when there is a name. */
    public AuthSession createSession(String cliId, Optional<String> deviceName) {

        if (StringUtils.isBlank(cliId)) {
            throw new IllegalArgumentException("cliId must not be blank");
        }

        CreateAuthSessionRequest request =
                new CreateAuthSessionRequest(cliId, deviceName.orElse(null));

        CreateAuthSessionResponse response = apiClient.post(
                ApiConstants.AUTH_SESSIONS,
                request,
                CreateAuthSessionResponse.class
        );

        return new AuthSession(response.sessionId(), URI.create(response.authorizationUrl()), response.authCode);
    }

    /**
     * Waits for the user to approve the login in the browser, over a single event stream.
     *
     * @return the access token and workspace; fails with an {@link ApiException} when the stream fails, or with a
     * {@link java.util.concurrent.TimeoutException} when the login isn't approved within 5 minutes
     */
    public CompletableFuture<AuthenticationEvent> waitForAuthentication(AuthSession session) {

        Objects.requireNonNull(session, "session must not be null");

        URI eventsUri = URI.create(apiClient.baseUrl() + ApiConstants.AUTH_SESSION_EVENTS.formatted(session.sessionId()));
        CompletableFuture<AuthenticationEvent> authentication = new CompletableFuture<>();

        SseConnection connection = sseClient.connect(eventsUri, event -> handleEvent(event, authentication));

        // Fail right away when the stream ends before a token arrived, instead of waiting for the timeout
        connection.completion().whenComplete((ignored, error) -> authentication.completeExceptionally(
                error != null ? unwrap(error) : new ApiException("The login was cancelled.")
        ));

        return authentication
                .orTimeout(AUTHENTICATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((token, error) -> connection.close());
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    private void handleEvent(SseEvent event, CompletableFuture<AuthenticationEvent> authentication) {

        if (!AUTHENTICATED_EVENT.equals(event.event())) {
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(event.data());

            JsonNode accessToken =
                    payload.get("accessToken");

            if (accessToken == null || accessToken.isNull() || accessToken.asText().isBlank()) {

                authentication.completeExceptionally(new ApiException("Authentication event did not contain an access token."));
                return;
            }

            authentication.complete(new AuthenticationEvent(
                    accessToken.asText(),
                    payload.path("workspace").path("name").asText(null)
            ));

        } catch (Exception e) {
            authentication.completeExceptionally(new ApiException("Unable to process authentication event.", e)
            );
        }
    }

    public void logout(String accessToken) {

        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }

        apiClient.postAuthorized(ApiConstants.AUTH_LOGOUT, accessToken);
    }

    // No deviceName field at all when the computer has no name, rather than null
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CreateAuthSessionRequest(String cliId, String deviceName) {
    }

    private record CreateAuthSessionResponse(
            String sessionId,
            String authorizationUrl,
            String authCode
    ) {
    }
}