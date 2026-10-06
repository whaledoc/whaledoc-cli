package io.whaledoc.webhook;

import io.whaledoc.exceptions.ForwardException;
import io.whaledoc.http.ApiClient;
import io.whaledoc.http.ApiConstants;
import io.whaledoc.http.NetworkErrors;
import io.whaledoc.http.SseClient;
import io.whaledoc.http.SseConnection;
import io.whaledoc.http.SseEvent;
import io.whaledoc.http.SseListener;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

public final class WebhookClient {

    private static final Duration FORWARD_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> FORWARD_SCHEMES = Set.of("http", "https");

    private final ApiClient apiClient;
    private final SseClient sseClient;
    private final HttpClient httpClient;

    public WebhookClient(ApiClient apiClient, SseClient sseClient, HttpClient httpClient) {
        this.apiClient = apiClient;
        this.sseClient = sseClient;
        this.httpClient = httpClient;
    }

    /**
     * Parses a --forward-to value. A missing scheme defaults to http, so {@code localhost:8080/events} works.
     */
    public static URI parseForwardUrl(String value) {

        String url = value.strip();

        if (!url.contains("://")) {
            url = "http://" + url;
        }

        try {
            URI uri = URI.create(url);

            if (!FORWARD_SCHEMES.contains(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid forward URL: " + value);
            }

            return uri;

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid forward URL: " + value, e);
        }
    }

    /**
     * Posts the event's data to the target and returns the response status.
     *
     * @throws ForwardException when the target can't be reached or doesn't respond with a 2xx status
     */
    public int forward(URI target, SseEvent event) {

        Objects.requireNonNull(target);
        Objects.requireNonNull(event);

        try {
            HttpResponse<Void> response = httpClient.send(createForwardRequest(target, event), HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ForwardException("%s responded with HTTP %d".formatted(target, response.statusCode()));
            }

            return response.statusCode();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ForwardException("Forwarding to %s was interrupted.".formatted(target), e);

        } catch (IOException e) {
            throw new ForwardException(NetworkErrors.describe(target, e), e);
        }
    }

    public SseConnection listen(String accessToken, Set<String> events, SseListener listener) {

        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken is required");
        }

        Objects.requireNonNull(events);
        Objects.requireNonNull(listener);

        URI uri = URI.create(apiClient.baseUrl() + ApiConstants.WEBHOOK_EVENTS);

        return sseClient.connect(uri, accessToken, listener.filter(
                event -> events.contains(WebhookEvents.ALL) || events.contains(event.event())
        ));
    }

    // Event type and ID headers let the local endpoint handle events like real webhook deliveries
    private static HttpRequest createForwardRequest(URI target, SseEvent event) {

        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .timeout(FORWARD_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(event.data()));

        if (event.event() != null) {
            request.header("WhaleDoc-Event", event.event());
        }

        if (event.id() != null) {
            request.header("WhaleDoc-Event-Id", event.id());
        }

        return request.build();
    }
}
