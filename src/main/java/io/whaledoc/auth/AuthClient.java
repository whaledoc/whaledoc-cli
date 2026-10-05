package io.whaledoc.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.http.*;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
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

    public AuthSession createSession(String cliId) {

        if (StringUtils.isBlank(cliId)) {
            throw new IllegalArgumentException("cliId must not be blank");
        }

        CreateAuthSessionRequest request =
                new CreateAuthSessionRequest(cliId);

        CreateAuthSessionResponse response = apiClient.post(
                ApiConstants.AUTH_LOGIN,
                request,
                CreateAuthSessionResponse.class
        );

        return new AuthSession(response.sessionId(), URI.create(response.authorizationUrl()), response.authCode);
    }

    /**
     * Waits for the user to approve the login in the browser, over a single event stream.
     *
     * @return the access token; fails with an {@link ApiException} when the stream fails, or with a
     * {@link java.util.concurrent.TimeoutException} when the login isn't approved within 5 minutes
     */
    public CompletableFuture<String> waitForAuthentication(AuthSession session) {

        Objects.requireNonNull(session, "session must not be null");

        URI eventsUri = URI.create(apiClient.baseUrl() + ApiConstants.AUTH_LOGIN_EVENT.formatted(session.sessionId()));
        CompletableFuture<String> accessToken = new CompletableFuture<>();

        SseConnection connection = sseClient.connect(eventsUri, event -> handleEvent(event, accessToken));

        // Fail right away when the stream ends before a token arrived, instead of waiting for the timeout
        connection.completion().whenComplete((ignored, error) -> accessToken.completeExceptionally(
                error != null ? unwrap(error) : new ApiException("The login was cancelled.")
        ));

        return accessToken
                .orTimeout(AUTHENTICATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((token, error) -> connection.close());
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    private void handleEvent(SseEvent event, CompletableFuture<String> authentication) {

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

            authentication.complete(
                    accessToken.asText()
            );

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

    private record CreateAuthSessionRequest(String cliId) {
    }

    private record CreateAuthSessionResponse(
            String sessionId,
            String authorizationUrl,
            String authCode
    ) {
    }
}