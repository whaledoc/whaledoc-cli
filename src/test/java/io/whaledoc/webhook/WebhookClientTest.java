package io.whaledoc.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.http.ApiClient;
import io.whaledoc.http.MessageListener;
import io.whaledoc.http.WebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@WireMockTest
class WebhookClientTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String EVENT_ID = "evt_01J9Z3K4XQ";
    private static final String BODY = "{\"id\":\"evt_01J9Z3K4XQ\",\"type\":\"job.completed\"}";
    private static final String SIGNATURE = "v1,K5oZfzN95Z9UVu1EsfQmfVNQhnkZ2pj9o9NDN/H/pI4=";
    private static final Map<String, String> HEADERS = Map.of(
            "Content-Type", "application/json",
            "webhook-id", EVENT_ID,
            "webhook-timestamp", "1792072803",
            "webhook-signature", SIGNATURE,
            "WhaleDoc-Event", "job.completed");
    private static final SignedEvent EVENT = new SignedEvent(EVENT_ID, "job.completed", HEADERS, BODY);

    private WebhookClient webhookClient;
    private String baseUrl;
    private final WebSocketClient webSocketClient = mock(WebSocketClient.class);

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {

        baseUrl = wireMock.getHttpBaseUrl();
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        ApiClient apiClient = new ApiClient(httpClient, objectMapper, baseUrl);
        HttpClient forwardClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        webhookClient = new WebhookClient(apiClient, webSocketClient, forwardClient, objectMapper);
    }

    @Test
    void shouldPostBodyWithSignedHeadersAsReceivedWhenForwardingEvent() {

        // given
        stubFor(post(urlEqualTo("/events")).willReturn(ok("received")));

        // when
        Optional<ForwardResult> result = webhookClient.forward(URI.create(baseUrl + "/events"), EVENT);

        // then
        assertThat(result).containsInstanceOf(ForwardResult.Answered.class);
        assertThat(((ForwardResult.Answered) result.orElseThrow()).body()).isEqualTo("received");
        verify(postRequestedFor(urlEqualTo("/events"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("webhook-id", equalTo(EVENT_ID))
                .withHeader("webhook-timestamp", equalTo("1792072803"))
                .withHeader("webhook-signature", equalTo(SIGNATURE))
                .withHeader("WhaleDoc-Event", equalTo("job.completed"))
                .withHeader("WhaleDoc-Event-Id", absent())
                .withRequestBody(equalTo(BODY)));
    }

    @Test
    void shouldReturnAnswerWhenForwardTargetRespondsWithError() {

        // given
        stubFor(post(urlEqualTo("/events")).willReturn(aResponse().withStatus(500).withBody("boom")));

        // when
        Optional<ForwardResult> result = webhookClient.forward(URI.create(baseUrl + "/events"), EVENT);

        // then
        assertThat(result).contains(new ForwardResult.Answered(500, "boom", durationOf(result)));
    }

    @Test
    void shouldReportRedirectWithoutFollowingItWhenForwardTargetRedirects() {

        // given
        stubFor(post(urlEqualTo("/events")).willReturn(aResponse().withStatus(307).withHeader("Location", "/elsewhere")));

        // when
        Optional<ForwardResult> result = webhookClient.forward(URI.create(baseUrl + "/events"), EVENT);

        // then
        assertThat(result).contains(new ForwardResult.Answered(307, "", durationOf(result)));
        verify(0, postRequestedFor(urlEqualTo("/elsewhere")));
    }

    @Test
    void shouldLeaveOutHeadersOtherThanWebhookHeadersWhenForwarding() {

        // given
        stubFor(post(urlEqualTo("/events")).willReturn(ok()));
        SignedEvent event = new SignedEvent(EVENT_ID, "job.completed",
                Map.of("webhook-id", EVENT_ID, "Host", "evil.example", "Cookie", "session=1"), BODY);

        // when
        webhookClient.forward(URI.create(baseUrl + "/events"), event);

        // then
        verify(postRequestedFor(urlEqualTo("/events"))
                .withHeader("webhook-id", equalTo(EVENT_ID))
                .withHeader("Cookie", absent()));
    }

    @Test
    void shouldForwardNonAsciiBodyByteForByteWhenForwarding() {

        // given
        stubFor(post(urlEqualTo("/events")).willReturn(ok()));
        String body = "{\"name\":\"Malmö – 東京\"}";
        SignedEvent event = new SignedEvent(EVENT_ID, "job.completed", HEADERS, body);

        // when
        webhookClient.forward(URI.create(baseUrl + "/events"), event);

        // then
        verify(postRequestedFor(urlEqualTo("/events")).withRequestBody(equalTo(body)));
    }

    @Test
    void shouldReturnConnectionFailureWhenForwardTargetIsUnreachable() {

        // given
        URI target = URI.create("http://localhost:1/events");

        // when
        Optional<ForwardResult> result = webhookClient.forward(target, EVENT);

        // then
        assertThat(result).contains(new ForwardResult.Unanswered(ForwardResult.Kind.CONNECTION_FAILED,
                "Unable to connect to localhost:1. Is the server running?", durationOf(result)));
    }

    @Test
    void shouldReadHeadersAndBodyWhenParsingEventMessage() {

        // given
        ListenMessage message = new ListenMessage("event", "job.completed", EVENT_ID, """
                {"type": "event", "headers": {"webhook-id": "evt_01J9Z3K4XQ", "webhook-signature": "v1,abc"}, "body": "{\\"a\\":1}"}
                """);
        SignedEvent expected = new SignedEvent(EVENT_ID, "job.completed",
                Map.of("webhook-id", EVENT_ID, "webhook-signature", "v1,abc"), "{\"a\":1}");

        // when
        SignedEvent event = webhookClient.parse(message);

        // then
        assertThat(event).isEqualTo(expected);
    }

    @Test
    void shouldIndentBodyWhenPrettyPrintingJson() {

        // given
        String body = "{\"id\":\"evt_1\",\"data\":{\"n\":1}}";

        // when
        String pretty = webhookClient.prettyJson(body);

        // then
        assertThat(pretty.lines()).hasSizeGreaterThan(3).anyMatch(line -> line.startsWith("  \"id\""));
    }

    @Test
    void shouldThrowApiExceptionWhenEventMessageHasNoBody() {

        // given
        ListenMessage message = new ListenMessage("event", "job.completed", EVENT_ID, "{\"headers\": {}}");

        // when
        Throwable thrown = catchThrowable(() -> webhookClient.parse(message));

        // then
        assertThat(thrown).isInstanceOf(ApiException.class).hasMessage("WhaleDoc sent an event the CLI can't read.");
    }

    @Test
    void shouldWriteAnswerMessageWhenForwardWasAnswered() {

        // given
        ForwardResult result = new ForwardResult.Answered(200, "x".repeat(3000), 42);
        String expected = """
                {"type": "answer", "eventId": "%s", "forwardUrl": "http://localhost:8080/events", "status": 200,
                 "durationMs": 42, "body": "%s", "failure": null}
                """.formatted(EVENT_ID, "x".repeat(2048));

        // when
        String message = webhookClient.answerMessage(URI.create("http://localhost:8080/events"), EVENT, result);

        // then
        assertThat(equalToJson(expected).match(message).isExactMatch()).isTrue();
    }

    @Test
    void shouldWriteFailureWithoutStatusWhenForwardWasNotAnswered() {

        // given
        ForwardResult result = new ForwardResult.Unanswered(ForwardResult.Kind.TIMEOUT,
                "localhost:8080 did not respond in time.", 10_000);
        String expected = """
                {"type": "answer", "eventId": "%s", "forwardUrl": "http://localhost:8080/events", "status": null,
                 "durationMs": 10000, "body": null, "failure": "TIMEOUT"}
                """.formatted(EVENT_ID);

        // when
        String message = webhookClient.answerMessage(URI.create("http://localhost:8080/events"), EVENT, result);

        // then
        assertThat(equalToJson(expected).match(message).isExactMatch()).isTrue();
    }

    @Test
    void shouldDefaultToHttpWhenForwardUrlHasNoScheme() {

        // given
        String value = "localhost:8080/events";

        // when
        URI actual = WebhookClient.parseForwardUrl(value);

        // then
        assertThat(actual).isEqualTo(URI.create("http://localhost:8080/events"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenForwardUrlHasUnsupportedScheme() {

        // given
        String value = "file:///etc/passwd";

        // when
        Throwable thrown = catchThrowable(() -> WebhookClient.parseForwardUrl(value));

        // then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid forward URL: " + value);
    }

    @Test
    void shouldDeliverRequestedEventsAndConnectionMessagesWhenListening() {

        // given
        List<ListenMessage> received = new ArrayList<>();
        MessageListener socket = givenListening(Set.of("job.completed"), received);

        // when
        socket.onMessage("{\"type\": \"ready\", \"signingSecret\": \"whsec_abc\"}");
        socket.onMessage("{\"type\": \"event\", \"event\": \"job.failed\", \"id\": \"evt_2\"}");
        socket.onMessage("{\"type\": \"event\", \"event\": \"job.completed\", \"id\": \"evt_1\"}");
        socket.onMessage("not json");

        // then
        assertThat(received).extracting(ListenMessage::type, ListenMessage::id)
                .containsExactly(tuple("ready", null),
                        tuple("event", "evt_1"));
    }

    @Test
    void shouldConnectToWebSocketWithForwardUrlWhenListeningWithForwardTarget() {

        // given
        URI forwardTo = URI.create("http://localhost:8080/events");
        URI expected = URI.create(baseUrl.replaceFirst("^http", "ws")
                + "/cli/webhooks/events?forwardTo=http%3A%2F%2Flocalhost%3A8080%2Fevents");

        // when
        webhookClient.listen(ACCESS_TOKEN, forwardTo, Set.of(WebhookEvents.ALL), message -> { });

        // then
        then(webSocketClient).should().connect(eq(expected), eq(ACCESS_TOKEN), any());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenAccessTokenIsBlank() {

        // given
        String accessToken = "";

        // when
        Throwable thrown = catchThrowable(() -> webhookClient.listen(accessToken, Set.of(WebhookEvents.ALL), event -> { }));

        // then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("accessToken is required");
    }

    @Test
    void shouldReturnTriggeredEventWhenApiAcceptsTestEvent() {

        // given
        givenTestEventIsAccepted("job.completed");
        TriggeredEvent expectedEvent = new TriggeredEvent("evt_01J9Z3K4XR", "job.completed");

        // when
        TriggeredEvent triggeredEvent = webhookClient.trigger(ACCESS_TOKEN, "job.completed");

        // then
        assertThat(triggeredEvent).isEqualTo(expectedEvent);
    }

    private static long durationOf(Optional<ForwardResult> result) {

        return result.orElseThrow().durationMs();
    }

    private void givenTestEventIsAccepted(String type) {

        stubFor(post(urlEqualTo("/cli/webhooks/test-events"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
                .withRequestBody(equalToJson("""
                        { "type": "%s" }
                        """.formatted(type)))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "id": "evt_01J9Z3K4XR", "type": "%s" }
                                """.formatted(type))));
    }

    /** Listening, with the listener the WebSocket client got, to hand it messages as WhaleDoc would. */
    private MessageListener givenListening(Set<String> events, List<ListenMessage> received) {

        webhookClient.listen(ACCESS_TOKEN, events, received::add);
        ArgumentCaptor<MessageListener> listener = ArgumentCaptor.forClass(MessageListener.class);
        then(webSocketClient).should().connect(any(), eq(ACCESS_TOKEN), listener.capture());
        return listener.getValue();
    }
}
