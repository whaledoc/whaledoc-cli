package io.whaledoc.http;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.whaledoc.exceptions.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SseClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final String EVENT_STREAM = """
            : keep-alive

            event: document.created
            id: 42
            data: {"id":"doc-1",
            data: "status":"created"}

            """;

    private final SseClient sseClient = new SseClient(HttpClient.newHttpClient());
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
        assertThat(connection.completion()).succeedsWithin(TIMEOUT);
    }

    @Test
    void shouldReportConnectedWhenStreamOpens(WireMockRuntimeInfo wireMock) {

        // given
        givenServerStreams("/events", EVENT_STREAM);

        // when
        connection = sseClient.connect(URI.create(wireMock.getHttpBaseUrl() + "/events"), event -> { });

        // then
        assertThat(connection.connected()).succeedsWithin(TIMEOUT);
    }

    @Test
    void shouldFailConnectionWhenServerRejectsAccessToken(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(get(urlEqualTo("/events")).willReturn(unauthorized()));

        // when
        connection = sseClient.connect(URI.create(wireMock.getHttpBaseUrl() + "/events"), "expired-token", event -> { });

        // then
        assertThat(connection.completion())
                .failsWithin(TIMEOUT)
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.statusCode()).isEqualTo(401));
    }

    @Test
    void shouldKeepDeliveringEventsWhenEventHandlerFails(WireMockRuntimeInfo wireMock) throws Exception {

        // given
        givenServerStreams("/events", """
                id: 1
                data: first

                id: 2
                data: second

                """);

        // when
        connection = sseClient.connect(URI.create(wireMock.getHttpBaseUrl() + "/events"), event -> {
            if (event.id().equals("1")) {
                throw new IllegalStateException("Handler failed");
            }

            receivedEvent.complete(event);
        });

        // then
        assertThat(receivedEvent.get(5, TimeUnit.SECONDS).id()).isEqualTo("2");
    }

    @Test
    void shouldResumeFromLastEventIdWhenReconnecting(WireMockRuntimeInfo wireMock) throws Exception {

        // given
        givenServerStreamsFromLastEventId();

        // when
        connection = sseClient.connect(URI.create(wireMock.getHttpBaseUrl() + "/events"), event -> {
            if (event.id().equals("43")) {
                receivedEvent.complete(event);
            }
        });

        // then
        assertThat(receivedEvent.get(5, TimeUnit.SECONDS).data()).isEqualTo("after reconnect");
    }

    // The first connection ends after event 42; the reconnect must ask to continue after it
    private void givenServerStreamsFromLastEventId() {

        stubFor(get(urlEqualTo("/events"))
                .withHeader("Last-Event-ID", absent())
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("id: 42\ndata: before reconnect\n\n")));

        stubFor(get(urlEqualTo("/events"))
                .withHeader("Last-Event-ID", equalTo("42"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("id: 43\ndata: after reconnect\n\n")));
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
}
