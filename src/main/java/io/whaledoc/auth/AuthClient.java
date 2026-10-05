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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    public String awaitAuthentication(AuthSession session) {

        Objects.requireNonNull(session, "session must not be null");

        URI eventsUri = URI.create(apiClient.baseUrl() + ApiConstants.AUTH_LOGIN_EVENT.formatted(session.sessionId()));

        CompletableFuture<String> authentication = new CompletableFuture<>();

        SseConnection connection = sseClient.connect(eventsUri, event -> handleEvent(event, authentication));

        try {
            return authentication.get(AUTHENTICATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            throw new ApiException("Authentication timed out.", e);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new ApiException("Authentication was interrupted.", e);

        } catch (Exception e) {
            throw new ApiException("Authentication failed.", e);

        } finally {
            connection.close();
        }
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