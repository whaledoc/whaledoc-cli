package io.whaledoc.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.whaledoc.exceptions.ApiException;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class WebhookClient {

    private static final Duration FORWARD_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> FORWARD_SCHEMES = Set.of("http", "https");
    /** The start of the local endpoint's answer that WhaleDoc keeps. */
    private static final int REPORTED_BODY_LENGTH = 2048;
    private static final String FORWARD_TO_PARAMETER = "?forwardTo=";
    /** Headers Java's HTTP client sets itself and refuses from callers. */
    private static final Set<String> CLIENT_HEADERS = Set.of("content-length", "host", "connection", "expect", "upgrade");

    private final ApiClient apiClient;
    private final SseClient sseClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebhookClient(ApiClient apiClient, SseClient sseClient, HttpClient httpClient, ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.sseClient = sseClient;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
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

    /** An event message of the stream: the type and id, with the request WhaleDoc signed for it. */
    public SignedEvent parse(SseEvent event) {

        JsonNode message = readTree(event.data());
        Map<String, String> headers = new LinkedHashMap<>();
        message.path("headers").properties().forEach(header -> headers.put(header.getKey(), header.getValue().asText()));

        return new SignedEvent(event.id(), event.event(), Map.copyOf(headers), message.path("body").asText());
    }

    /** A field of a stream message about the stream itself, such as the signing secret of {@code ready}. */
    public Optional<String> field(SseEvent event, String name) {

        return Optional.ofNullable(readTree(event.data()).get(name)).map(JsonNode::asText);
    }

    /**
     * Posts the event's body to the target with the headers WhaleDoc signed, exactly as received, and returns what the
     * target answered, or why it didn't.
     */
    public ForwardResult forward(URI target, SignedEvent event) {

        Objects.requireNonNull(target);
        Objects.requireNonNull(event);
        long start = System.nanoTime();

        try {
            HttpResponse<String> response = httpClient.send(createForwardRequest(target, event),
                    HttpResponse.BodyHandlers.ofString());
            return ForwardResult.answered(response.statusCode(), response.body(), millisSince(start));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ForwardResult.failed(ForwardResult.Kind.CONNECTION_FAILED,
                    "Forwarding to %s was interrupted.".formatted(target), millisSince(start));

        } catch (IOException e) {
            return ForwardResult.failed(failureKind(e), NetworkErrors.describe(target, e), millisSince(start));
        }
    }

    /** Tells WhaleDoc what the local endpoint answered, for the app's Events tab. */
    public void reportAnswer(String accessToken, URI target, SignedEvent event, ForwardResult result) {

        LocalAnswer answer = new LocalAnswer(target.toString(), result.status().orElse(null), result.durationMs(),
                result.body().map(body -> StringUtils.truncate(body, REPORTED_BODY_LENGTH)).orElse(null),
                result.failure().map(failure -> failure.kind().name()).orElse(null));

        apiClient.postAuthorizedWithoutAnswer(ApiConstants.WEBHOOK_EVENT_RESPONSES.formatted(event.id()), accessToken,
                answer);
    }

    /** Asks the API to send a test event of this type, with sample data, to the logged-in workspace. */
    public TriggeredEvent trigger(String accessToken, String type) {

        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken is required");
        }

        return apiClient.postAuthorized(ApiConstants.WEBHOOK_TEST_EVENTS, accessToken, new TriggerRequest(type),
                TriggeredEvent.class);
    }

    /**
     * Opens the event stream. {@code forwardTo} is shown to teammates on the app's Webhooks page. Messages about the
     * stream itself always come through; events only when {@code events} names them or is {@link WebhookEvents#ALL}.
     */
    public SseConnection listen(String accessToken, Optional<URI> forwardTo, Set<String> events, SseListener listener) {

        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken is required");
        }

        Objects.requireNonNull(events);
        Objects.requireNonNull(listener);

        String query = forwardTo
                .map(target -> FORWARD_TO_PARAMETER + URLEncoder.encode(target.toString(), StandardCharsets.UTF_8))
                .orElse("");
        URI uri = URI.create(apiClient.baseUrl() + ApiConstants.WEBHOOK_EVENTS + query);

        return sseClient.connect(uri, accessToken, listener.filter(event ->
                WebhookEvents.STREAM_MESSAGES.contains(event.event())
                        || events.contains(WebhookEvents.ALL)
                        || events.contains(event.event())));
    }

    private JsonNode readTree(String data) {

        try {
            return objectMapper.readTree(data);
        } catch (JsonProcessingException e) {
            throw new ApiException("WhaleDoc sent a message the CLI can't read.", e);
        }
    }

    private static HttpRequest createForwardRequest(URI target, SignedEvent event) {

        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .timeout(FORWARD_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(event.body()));
        event.headers().entrySet().stream()
                .filter(header -> !CLIENT_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT)))
                .forEach(header -> request.header(header.getKey(), header.getValue()));

        return request.build();
    }

    /** No answer in time is a timeout; not reaching the endpoint at all, including a slow connect, is a failure. */
    private static ForwardResult.Kind failureKind(IOException error) {

        boolean timedOut = error instanceof HttpTimeoutException && !(error instanceof HttpConnectTimeoutException);
        return timedOut ? ForwardResult.Kind.TIMEOUT : ForwardResult.Kind.CONNECTION_FAILED;
    }

    private static long millisSince(long startNanos) {

        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private record TriggerRequest(String type) {
    }

    private record LocalAnswer(String forwardUrl, Integer status, long durationMs, String body, String failure) {
    }
}
