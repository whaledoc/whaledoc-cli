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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

@Slf4j
public final class WebhookClient {

    private static final Duration FORWARD_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> FORWARD_SCHEMES = Set.of("http", "https");
    /** The start of the local endpoint's answer that WhaleDoc keeps. */
    private static final int REPORTED_BODY_LENGTH = 2048;
    /** Enough bytes for {@link #REPORTED_BODY_LENGTH} characters in any encoding; the rest is never read. */
    private static final int READ_BODY_BYTES = REPORTED_BODY_LENGTH * 4;
    private static final String FORWARD_TO_PARAMETER = "?forwardTo=";
    /** The headers a webhook delivery carries; anything else from the stream is left out. */
    private static final Set<String> FORWARDED_HEADERS = Set.of("content-type", "user-agent");
    private static final Set<String> FORWARDED_HEADER_PREFIXES = Set.of("webhook-", "whaledoc-");
    /** Closing the stream ends a read still waiting here. */
    private static final ExecutorService BODY_READERS = Executors.newVirtualThreadPerTaskExecutor();

    private final ApiClient apiClient;
    private final SseClient sseClient;
    private final HttpClient forwardClient;
    private final ObjectMapper objectMapper;

    /** {@code forwardClient} follows no redirects: a redirect is the local endpoint's answer, reported as it is. */
    public WebhookClient(ApiClient apiClient, SseClient sseClient, HttpClient forwardClient, ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.sseClient = sseClient;
        this.forwardClient = forwardClient;
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
        JsonNode body = message.path("body");

        if (!body.isTextual()) {
            throw new ApiException("WhaleDoc sent an event the CLI can't read.");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        message.path("headers").properties().forEach(header ->
                text(header.getValue()).ifPresent(value -> headers.put(header.getKey(), value)));

        return new SignedEvent(event.id(), event.event(), Collections.unmodifiableMap(headers), body.asText());
    }

    /** The event's body, indented, as {@code --print-json} shows it; as received when it isn't JSON. */
    public String prettyJson(String body) {

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(body));
        } catch (JsonProcessingException e) {
            return body;
        }
    }

    /** A field of a stream message about the stream itself, such as the signing secret of {@code ready}. */
    public Optional<String> field(SseEvent event, String name) {

        return Optional.ofNullable(readTree(event.data()).get(name)).flatMap(WebhookClient::text);
    }

    /**
     * Posts the event's body to the target with the headers WhaleDoc signed, exactly as received, and returns what the
     * target answered, or why it didn't. Empty when it was interrupted, as when the user quits.
     */
    public Optional<ForwardResult> forward(URI target, SignedEvent event) {

        Objects.requireNonNull(target);
        Objects.requireNonNull(event);
        long start = System.nanoTime();

        try {
            HttpResponse<InputStream> response = forwardClient.send(createForwardRequest(target, event),
                    HttpResponse.BodyHandlers.ofInputStream());
            String body = readStart(response.body(), FORWARD_TIMEOUT.minus(Duration.ofNanos(System.nanoTime() - start)));
            return Optional.of(new ForwardResult.Answered(response.statusCode(), body, millisSince(start)));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();

        } catch (IOException e) {
            return Optional.of(new ForwardResult.Unanswered(failureKind(e), NetworkErrors.describe(target, e),
                    millisSince(start)));
        }
    }

    /** Tells WhaleDoc what the local endpoint answered, for the app's Events tab. */
    public void reportAnswer(String accessToken, URI target, SignedEvent event, ForwardResult result) {

        if (StringUtils.isBlank(event.id())) {
            return;
        }

        String path = ApiConstants.WEBHOOK_EVENT_RESPONSES.formatted(URLEncoder.encode(event.id(), StandardCharsets.UTF_8));
        apiClient.postAuthorizedWithoutAnswer(path, accessToken, toLocalAnswer(target, result));
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
     * Opens the event stream. Messages about the stream itself always come through; events only when {@code events}
     * names them or is {@link WebhookEvents#ALL}.
     */
    public SseConnection listen(String accessToken, Set<String> events, SseListener listener) {

        return connect(accessToken, "", events, listener);
    }

    /** As {@link #listen(String, Set, SseListener)}, telling WhaleDoc where events are forwarded, for the app to show. */
    public SseConnection listen(String accessToken, URI forwardTo, Set<String> events, SseListener listener) {

        String query = FORWARD_TO_PARAMETER + URLEncoder.encode(forwardTo.toString(), StandardCharsets.UTF_8);
        return connect(accessToken, query, events, listener);
    }

    private SseConnection connect(String accessToken, String query, Set<String> events, SseListener listener) {

        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken is required");
        }

        Objects.requireNonNull(events);
        Objects.requireNonNull(listener);

        URI uri = URI.create(apiClient.baseUrl() + ApiConstants.WEBHOOK_EVENTS + query);
        return sseClient.connect(uri, accessToken, listener.filter(event -> isWanted(event, events)));
    }

    private static boolean isWanted(SseEvent event, Set<String> events) {

        if (event.event() == null) {
            return events.contains(WebhookEvents.ALL);
        }

        return WebhookEvents.STREAM_MESSAGES.contains(event.event())
                || events.contains(WebhookEvents.ALL)
                || events.contains(event.event());
    }

    private static LocalAnswer toLocalAnswer(URI target, ForwardResult result) {

        return switch (result) {
            case ForwardResult.Answered answered -> new LocalAnswer(target.toString(), answered.status(),
                    answered.durationMs(), StringUtils.truncate(answered.body(), REPORTED_BODY_LENGTH), null);
            case ForwardResult.Unanswered unanswered -> new LocalAnswer(target.toString(), null,
                    unanswered.durationMs(), null, unanswered.kind().name());
        };
    }

    private JsonNode readTree(String data) {

        try {
            return objectMapper.readTree(data);
        } catch (JsonProcessingException e) {
            throw new ApiException("WhaleDoc sent a message the CLI can't read.", e);
        }
    }

    /** A JSON string, number or boolean as text; nothing for null, objects and arrays. */
    private static Optional<String> text(JsonNode node) {

        return Optional.of(node)
                .filter(JsonNode::isValueNode)
                .filter(Predicate.not(JsonNode::isNull))
                .map(JsonNode::asText);
    }

    /**
     * The start of the answer, read within what is left of the wait, then the stream is closed: a long, slow or endless
     * answer never holds up listening. Empty when the start didn't arrive in time.
     */
    private static String readStart(InputStream body, Duration within) throws IOException, InterruptedException {

        try (body) {
            Future<byte[]> read = BODY_READERS.submit(() -> body.readNBytes(READ_BODY_BYTES));
            return new String(read.get(Math.max(0, within.toMillis()), TimeUnit.MILLISECONDS), StandardCharsets.UTF_8);

        } catch (TimeoutException e) {
            return "";

        } catch (ExecutionException e) {
            throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
        }
    }

    private static HttpRequest createForwardRequest(URI target, SignedEvent event) {

        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .timeout(FORWARD_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(event.body(), StandardCharsets.UTF_8));
        event.headers().entrySet().stream()
                .filter(header -> isForwarded(header.getKey()))
                .forEach(header -> addHeader(request, header.getKey(), header.getValue()));

        return request.build();
    }

    private static boolean isForwarded(String headerName) {

        String name = headerName.toLowerCase(Locale.ROOT);
        return FORWARDED_HEADERS.contains(name) || FORWARDED_HEADER_PREFIXES.stream().anyMatch(name::startsWith);
    }

    /** A header Java's HTTP client refuses is left out; the endpoint then sees an event its signature check rejects. */
    private static void addHeader(HttpRequest.Builder request, String name, String value) {

        try {
            request.header(name, value);
        } catch (IllegalArgumentException e) {
            log.debug("Header {} left out of the forwarded request", name, e);
        }
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

    /** The report's JSON: absent values are {@code null}, as WhaleDoc expects. */
    private record LocalAnswer(String forwardUrl, Integer status, long durationMs, String body, String failure) {
    }
}
