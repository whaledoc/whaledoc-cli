package io.whaledoc.http;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@WireMockTest
class SseClientTest {

    private static final String EVENT_STREAM = """
            : keep-alive

            event: document.created
            id: 42
            data: {"id":"doc-1",
            data: "status":"created"}

            """;

    private final SseClient sseClient = new SseClient();
    private final CompletableFuture<SseEvent> receivedEvent = new CompletableFuture<>();

    private SseConnection connection;

    @AfterEach
    void tearDown() {

        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void shouldDeliverEventWhenServerSendsEvent(WireMockRuntimeInfo wireMock) throws Exception {

        // given
        givenServerStreams("/events", EVENT_STREAM);
        SseEvent expectedEvent = createExpectedEvent();

        // when
        connection = sseClient.connect(URI.create(wireMock.getHttpBaseUrl() + "/events"), receivedEvent::complete);

        // then
        assertThat(receivedEvent.get(5, TimeUnit.SECONDS)).usingRecursiveComparison().isEqualTo(expectedEvent);
    }

    @Test
    void shouldDeliverEventWhenAccessTokenIsAccepted(WireMockRuntimeInfo wireMock) throws Exception {

        // given
        givenServerStreamsOnlyWithAccessToken("access-token");
        SseEvent expectedEvent = createExpectedEvent();

        // when
        connection = sseClient.connect(URI.create(wireMock.getHttpBaseUrl() + "/events"), "access-token", receivedEvent::complete);

        // then
        assertThat(receivedEvent.get(5, TimeUnit.SECONDS)).usingRecursiveComparison().isEqualTo(expectedEvent);
    }

    @Test
    void shouldCompleteConnectionWhenConnectionIsClosed(WireMockRuntimeInfo wireMock) {

        // given
        givenServerStreams("/events", EVENT_STREAM);
        connection = sseClient.connect(URI.create(wireMock.getHttpBaseUrl() + "/events"), event -> { });

        // when
        connection.close();

        // then
        assertThat(connection.isOpen()).isFalse();
        assertThatCode(() -> CompletableFuture.runAsync(this::awaitCompletion).get(5, TimeUnit.SECONDS))
                .doesNotThrowAnyException();
    }

    private void givenServerStreams(String path, String body) {

        stubFor(get(urlEqualTo(path))
                .withHeader("Accept", equalTo("text/event-stream"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(body)));
    }

    private void givenServerStreamsOnlyWithAccessToken(String accessToken) {

        stubFor(get(urlEqualTo("/events"))
                .withHeader("Authorization", equalTo("Bearer " + accessToken))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(EVENT_STREAM)));
    }

    private SseEvent createExpectedEvent() {
        return new SseEvent("document.created", "{\"id\":\"doc-1\",\n\"status\":\"created\"}", "42");
    }

    private void awaitCompletion() {

        try {
            connection.awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
