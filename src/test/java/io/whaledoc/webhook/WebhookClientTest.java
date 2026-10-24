package io.whaledoc.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.http.ApiClient;
import io.whaledoc.http.SseClient;
import io.whaledoc.http.SseConnection;
import io.whaledoc.http.SseEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
    private SseConnection connection;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {

        baseUrl = wireMock.getHttpBaseUrl();
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        ApiClient apiClient = new ApiClient(httpClient, objectMapper, baseUrl);
        HttpClient forwardClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        webhookClient = new WebhookClient(apiClient, new SseClient(httpClient), forwardClient, objectMapper);
    }

    @AfterEach
    void tearDown() {

        if (connection != null) {
            connection.close();
        }
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
    void shouldReadHeadersAndBodyWhenParsingStreamMessage() {

        // given
        SseEvent message = new SseEvent("job.completed", """
                {"headers": {"webhook-id": "evt_01J9Z3K4XQ", "webhook-signature": "v1,abc"}, "body": "{\\"a\\":1}"}
                """, EVENT_ID);
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
    void shouldThrowApiExceptionWhenStreamMessageHasNoBody() {

        // given
        SseEvent message = new SseEvent("job.completed", "{\"headers\": {}}", EVENT_ID);

        // when
        Throwable thrown = catchThrowable(() -> webhookClient.parse(message));

        // then
        assertThat(thrown).isInstanceOf(ApiException.class).hasMessage("WhaleDoc sent an event the CLI can't read.");
    }

    @Test
    void shouldReportLocalAnswerWhenForwardWasAnswered() {

        // given
        stubFor(post(urlEqualTo("/cli/webhooks/events/" + EVENT_ID + "/responses")).willReturn(noContent()));
        ForwardResult result = new ForwardResult.Answered(200, "x".repeat(3000), 42);

        // when
        webhookClient.reportAnswer(ACCESS_TOKEN, URI.create("http://localhost:8080/events"), EVENT, result);

        // then
        verify(postRequestedFor(urlEqualTo("/cli/webhooks/events/" + EVENT_ID + "/responses"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
                .withRequestBody(equalToJson("""
                        {"forwardUrl": "http://localhost:8080/events", "status": 200, "durationMs": 42,
                         "body": "%s", "failure": null}
                        """.formatted("x".repeat(2048)))));
    }

    @Test
    void shouldReportFailureWithoutStatusWhenForwardWasNotAnswered() {

        // given
        stubFor(post(urlEqualTo("/cli/webhooks/events/" + EVENT_ID + "/responses")).willReturn(noContent()));
        ForwardResult result = new ForwardResult.Unanswered(ForwardResult.Kind.TIMEOUT,
                "localhost:8080 did not respond in time.", 10_000);

        // when
        webhookClient.reportAnswer(ACCESS_TOKEN, URI.create("http://localhost:8080/events"), EVENT, result);

        // then
        verify(postRequestedFor(urlEqualTo("/cli/webhooks/events/" + EVENT_ID + "/responses"))
                .withRequestBody(equalToJson("""
                        {"forwardUrl": "http://localhost:8080/events", "status": null, "durationMs": 10000,
                         "body": null, "failure": "TIMEOUT"}
                        """)));
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
    void shouldDeliverRequestedEventsAndStreamMessagesWhenListening() throws Exception {

        // given
        SseEvent ready = new SseEvent("ready", "{\"signingSecret\":\"whsec_abc\"}", null);
        SseEvent failed = new SseEvent("job.failed", "{}", "2");
        SseEvent completed = new SseEvent("job.completed", "{}", "1");
        givenWebhookEventsAreStreamed("/cli/webhooks/events", ready, failed, completed);
        List<SseEvent> receivedEvents = new CopyOnWriteArrayList<>();
        CompletableFuture<SseEvent> completedReceived = new CompletableFuture<>();

        // when
        connection = webhookClient.listen(ACCESS_TOKEN, Set.of("job.completed"), event -> {
            receivedEvents.add(event);
            if ("job.completed".equals(event.event())) {
                completedReceived.complete(event);
            }
        });

        // then
        completedReceived.get(5, TimeUnit.SECONDS);
        assertThat(receivedEvents).extracting(SseEvent::event).containsExactly("ready", "job.completed");
    }

    @Test
    void shouldSendForwardUrlWhenListeningWithForwardTarget() throws Exception {

        // given
        SseEvent ready = new SseEvent("ready", "{\"signingSecret\":\"whsec_abc\"}", null);
        givenWebhookEventsAreStreamed("/cli/webhooks/events?forwardTo=http%3A%2F%2Flocalhost%3A8080%2Fevents", ready);
        CompletableFuture<SseEvent> readyReceived = new CompletableFuture<>();

        // when
        connection = webhookClient.listen(ACCESS_TOKEN, URI.create("http://localhost:8080/events"),
                Set.of(WebhookEvents.ALL), readyReceived::complete);

        // then
        assertThat(readyReceived.get(5, TimeUnit.SECONDS).event()).isEqualTo("ready");
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

    private void givenWebhookEventsAreStreamed(String url, SseEvent... events) {

        StringBuilder body = new StringBuilder();

        for (SseEvent event : events) {
            if (event.id() != null) {
                body.append("id: %s%n".formatted(event.id()));
            }
            body.append("event: %s%ndata: %s%n%n".formatted(event.event(), event.data()));
        }

        stubFor(get(urlEqualTo(url))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(body.toString())));
    }
}
