package io.whaledoc.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.whaledoc.exceptions.ForwardException;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@WireMockTest
class WebhookClientTest {

    private static final SseEvent CREATED_EVENT = new SseEvent("document.created", "{\"id\":\"doc-1\"}", "1");
    private static final SseEvent FAILED_EVENT = new SseEvent("document.failed", "{\"id\":\"doc-2\"}", "2");

    private WebhookClient webhookClient;
    private String baseUrl;
    private SseConnection connection;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {

        baseUrl = wireMock.getHttpBaseUrl();
        HttpClient httpClient = HttpClient.newHttpClient();
        ApiClient apiClient = new ApiClient(httpClient, new ObjectMapper(), baseUrl);
        webhookClient = new WebhookClient(apiClient, new SseClient(httpClient), httpClient);
    }

    @AfterEach
    void tearDown() {

        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void shouldPostEventWithEventHeadersWhenForwardingEvent() {

        // given
        stubFor(post(urlEqualTo("/events")).willReturn(ok()));

        // when
        int status = webhookClient.forward(URI.create(baseUrl + "/events"), CREATED_EVENT);

        // then
        assertThat(status).isEqualTo(200);
        verify(postRequestedFor(urlEqualTo("/events"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("WhaleDoc-Event", equalTo("document.created"))
                .withHeader("WhaleDoc-Event-Id", equalTo("1"))
                .withRequestBody(equalToJson(CREATED_EVENT.data())));
    }

    @Test
    void shouldThrowForwardExceptionWhenForwardTargetRespondsWithError() {

        // given
        stubFor(post(urlEqualTo("/events")).willReturn(serverError()));
        URI target = URI.create(baseUrl + "/events");

        // when
        Throwable thrown = catchThrowable(() -> webhookClient.forward(target, CREATED_EVENT));

        // then
        assertThat(thrown)
                .isInstanceOf(ForwardException.class)
                .hasMessage(target + " responded with HTTP 500");
    }

    @Test
    void shouldThrowForwardExceptionWhenForwardTargetIsUnreachable() {

        // given
        URI target = URI.create("http://localhost:1/events");

        // when
        Throwable thrown = catchThrowable(() -> webhookClient.forward(target, CREATED_EVENT));

        // then
        assertThat(thrown)
                .isInstanceOf(ForwardException.class)
                .hasMessage("Unable to reach " + target);
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
    void shouldDeliverOnlyRequestedEventsWhenListening() throws Exception {

        // given
        givenWebhookEventsAreStreamed(FAILED_EVENT, CREATED_EVENT);
        List<SseEvent> receivedEvents = new CopyOnWriteArrayList<>();
        CompletableFuture<SseEvent> createdEventReceived = new CompletableFuture<>();

        // when
        connection = webhookClient.listen("access-token", Set.of("document.created"), event -> {
            receivedEvents.add(event);
            createdEventReceived.complete(event);
        });

        // then
        createdEventReceived.get(5, TimeUnit.SECONDS);
        assertThat(receivedEvents).usingRecursiveFieldByFieldElementComparator().containsExactly(CREATED_EVENT);
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

    private void givenWebhookEventsAreStreamed(SseEvent... events) {

        StringBuilder body = new StringBuilder();

        for (SseEvent event : events) {
            body.append("id: %s%nevent: %s%ndata: %s%n%n".formatted(event.id(), event.event(), event.data()));
        }

        stubFor(get(urlEqualTo("/cli/webhooks/events"))
                .withHeader("Authorization", equalTo("Bearer access-token"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(body.toString())));
    }
}
