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
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class WebhookClient {

    private static final Duration FORWARD_TIMEOUT = Duration.ofSeconds(10);
    private static final String JSON = "application/json";
    private static final Set<String> FORWARD_SCHEMES = Set.of("http", "https");

    private final ApiClient apiClient;
    private final SseClient sseClient;
    private final HttpClient httpClient;
    private final Clock clock;

    public WebhookClient(ApiClient apiClient, SseClient sseClient, HttpClient httpClient, Clock clock) {
        this.apiClient = apiClient;
        this.sseClient = sseClient;
        this.httpClient = httpClient;
        this.clock = clock;
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
     * Posts the event to the target as a real delivery would: the same body, signed with {@code secret}.
     *
     * @return the response status
     * @throws ForwardException when the target can't be reached or doesn't respond with a 2xx status
     */
    public int forward(URI target, SseEvent event, String secret) {

        Objects.requireNonNull(target);
        Objects.requireNonNull(event);
        Objects.requireNonNull(secret);

        try {
            HttpResponse<Void> response = httpClient.send(createForwardRequest(target, event, secret),
                    HttpResponse.BodyHandlers.discarding());

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

    /** Asks the API to send a test event of this type, with sample data, to the logged-in workspace. */
    public TriggeredEvent trigger(String accessToken, String type) {

        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken is required");
        }

        return apiClient.postAuthorized(ApiConstants.WEBHOOK_TEST_EVENTS, accessToken, new TriggerRequest(type),
                TriggeredEvent.class);
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

    // The Standard Webhooks headers of a real delivery, so the local endpoint verifies and handles it the same way
    private HttpRequest createForwardRequest(URI target, SseEvent event, String secret) {

        String webhookId = Optional.ofNullable(event.id()).orElse("");
        long timestamp = clock.instant().getEpochSecond();

        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .timeout(FORWARD_TIMEOUT)
                .header(ForwardHeaders.CONTENT_TYPE, JSON)
                .header(ForwardHeaders.WEBHOOK_ID, webhookId)
                .header(ForwardHeaders.WEBHOOK_TIMESTAMP, Long.toString(timestamp))
                .header(ForwardHeaders.WEBHOOK_SIGNATURE,
                        WebhookSigner.signature(secret, webhookId, timestamp, event.data()))
                .POST(HttpRequest.BodyPublishers.ofString(event.data()));

        Optional.ofNullable(event.event()).ifPresent(type -> request.header(ForwardHeaders.EVENT, type));
        return request.build();
    }

    private record TriggerRequest(String type) {
    }
}
