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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
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

    private AuthClient authClient;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {

        ObjectMapper objectMapper = new ObjectMapper();
        ApiClient apiClient = new ApiClient(objectMapper, wireMock.getHttpBaseUrl());
        authClient = new AuthClient(apiClient, new SseClient(), objectMapper);
    }

    @Test
    void shouldReturnSessionWhenSessionIsCreated() {

        // given
        givenSessionCanBeCreated();
        AuthSession expectedSession = createExpectedSession();

        // when
        AuthSession actualSession = authClient.createSession(CLI_ID);

        // then
        assertThat(actualSession).usingRecursiveComparison().isEqualTo(expectedSession);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenCliIdIsBlank() {

        // given
        String cliId = " ";

        // when
        Throwable thrown = catchThrowable(() -> authClient.createSession(cliId));

        // then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cliId must not be blank");
    }

    @Test
    void shouldReturnAccessTokenWhenAuthenticationEventArrives() {

        // given
        givenAuthenticationEventIsSent("""
                { "accessToken": "access-token" }
                """);

        // when
        String actualAccessToken = authClient.awaitAuthentication(createExpectedSession());

        // then
        assertThat(actualAccessToken).isEqualTo("access-token");
    }

    @Test
    void shouldThrowApiExceptionWhenAuthenticationEventHasNoAccessToken() {

        // given
        givenAuthenticationEventIsSent("""
                { "accessToken": "" }
                """);

        // when
        Throwable thrown = catchThrowable(() -> authClient.awaitAuthentication(createExpectedSession()));

        // then
        assertThat(thrown)
                .isInstanceOf(ApiException.class)
                .hasMessage("Authentication failed.")
                .hasRootCauseMessage("Authentication event did not contain an access token.");
    }

    @Test
    void shouldSendAccessTokenWhenLoggingOut() {

        // given
        stubFor(any(urlEqualTo("/cli/auth/logout")).willReturn(noContent()));

        // when
        authClient.logout("access-token");

        // then
        verify(anyRequestedFor(urlEqualTo("/cli/auth/logout"))
                .withHeader("Authorization", equalTo("Bearer access-token")));
    }

    private void givenSessionCanBeCreated() {

        stubFor(post(urlEqualTo("/cli/auth/login"))
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
    }

    private void givenAuthenticationEventIsSent(String data) {

        stubFor(get(urlEqualTo("/cli/auth/login/" + SESSION_ID))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("event: authenticated%ndata: %s%n%n".formatted(data.strip()))));
    }

    private AuthSession createExpectedSession() {
        return new AuthSession(SESSION_ID, URI.create("https://app.whaledoc.io/cli/authorize/session-1"), "ABCD-1234");
    }
}
