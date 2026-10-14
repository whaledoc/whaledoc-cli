package io.whaledoc.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.http.ApiClient;
import io.whaledoc.http.SseClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@WireMockTest
class AuthClientTest {

    private static final String CLI_ID = "cli-1";
    private static final String SESSION_ID = "session-1";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private AuthClient authClient;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {

        ObjectMapper objectMapper = new ObjectMapper();
        HttpClient httpClient = HttpClient.newHttpClient();
        ApiClient apiClient = new ApiClient(httpClient, objectMapper, wireMock.getHttpBaseUrl());
        authClient = new AuthClient(apiClient, new SseClient(httpClient), objectMapper);
    }

    @Test
    void shouldReturnSessionWhenSessionIsCreated() {

        // given
        givenSessionCanBeCreated();
        AuthSession expectedSession = createExpectedSession();

        // when
        AuthSession actualSession = authClient.createSession(CLI_ID, Optional.of("dev-laptop"));

        // then
        assertThat(actualSession).usingRecursiveComparison().isEqualTo(expectedSession);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenCliIdIsBlank() {

        // given
        String cliId = " ";

        // when
        Throwable thrown = catchThrowable(() -> authClient.createSession(cliId, Optional.empty()));

        // then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cliId must not be blank");
    }

    @Test
    void shouldReturnAccessTokenAndWorkspaceWhenAuthenticationEventArrives() {

        // given
        givenAuthenticationEventIsSent("""
                { "accessToken": "access-token", "workspace": { "id": "ws-1", "name": "Acme" } }
                """);
        AuthenticationEvent expectedEvent = new AuthenticationEvent("access-token", "Acme");

        // when
        CompletableFuture<AuthenticationEvent> authentication = authClient.waitForAuthentication(createExpectedSession());

        // then
        assertThat(authentication).succeedsWithin(TIMEOUT).isEqualTo(expectedEvent);
    }

    @Test
    void shouldReturnAccessTokenWhenAuthenticationEventHasNoWorkspace() {

        // given
        givenAuthenticationEventIsSent("""
                { "accessToken": "access-token" }
                """);
        AuthenticationEvent expectedEvent = new AuthenticationEvent("access-token", null);

        // when
        CompletableFuture<AuthenticationEvent> authentication = authClient.waitForAuthentication(createExpectedSession());

        // then
        assertThat(authentication).succeedsWithin(TIMEOUT).isEqualTo(expectedEvent);
    }

    @Test
    void shouldFailWithApiExceptionWhenAuthenticationEventHasNoAccessToken() {

        // given
        givenAuthenticationEventIsSent("""
                { "accessToken": "" }
                """);

        // when
        CompletableFuture<AuthenticationEvent> authentication = authClient.waitForAuthentication(createExpectedSession());

        // then
        assertThat(authentication)
                .failsWithin(TIMEOUT)
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(ApiException.class)
                .withMessage("Authentication event did not contain an access token.");
    }

    @Test
    void shouldFailRightAwayWhenLoginSessionIsUnknown() {

        // given
        stubFor(get(urlEqualTo("/cli/auth/sessions/" + SESSION_ID + "/events")).willReturn(notFound()));

        // when
        CompletableFuture<AuthenticationEvent> authentication = authClient.waitForAuthentication(createExpectedSession());

        // then
        assertThat(authentication)
                .failsWithin(TIMEOUT)
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.statusCode()).isEqualTo(404));
    }

    @Test
    void shouldSendAccessTokenWhenLoggingOut() {

        // given
        stubFor(post(urlEqualTo("/cli/auth/logout")).willReturn(noContent()));

        // when
        authClient.logout("access-token");

        // then
        verify(postRequestedFor(urlEqualTo("/cli/auth/logout"))
                .withHeader("Authorization", equalTo("Bearer access-token")));
    }

    @Test
    void shouldLeaveDeviceNameOutWhenComputerHasNoName() {

        // given
        stubFor(post(urlEqualTo("/cli/auth/sessions"))
                .withRequestBody(equalToJson("""
                        { "cliId": "cli-1" }
                        """))
                .willReturn(okJson("""
                        {
                          "sessionId": "session-1",
                          "authorizationUrl": "https://app.whaledoc.io/cli/authorize/session-1",
                          "authCode": "ABCD-1234"
                        }
                        """)));

        // when
        Throwable thrown = catchThrowable(() -> authClient.createSession(CLI_ID, Optional.empty()));

        // then
        assertThat(thrown).isNull();
    }

    private void givenSessionCanBeCreated() {

        stubFor(post(urlEqualTo("/cli/auth/sessions"))
                .withRequestBody(equalToJson("""
                        { "cliId": "cli-1", "deviceName": "dev-laptop" }
                        """))
                .willReturn(okJson("""
                        {
                          "sessionId": "session-1",
                          "authorizationUrl": "https://app.whaledoc.io/cli/authorize/session-1",
                          "authCode": "ABCD-1234"
                        }
                        """)));
    }

    private void givenAuthenticationEventIsSent(String data) {

        stubFor(get(urlEqualTo("/cli/auth/sessions/" + SESSION_ID + "/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("event: authenticated%ndata: %s%n%n".formatted(data.strip()))));
    }

    private AuthSession createExpectedSession() {
        return new AuthSession(SESSION_ID, URI.create("https://app.whaledoc.io/cli/authorize/session-1"), "ABCD-1234");
    }
}
