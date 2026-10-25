package io.whaledoc.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.http.ApiClient;
import io.whaledoc.http.ApiConstants;
import io.whaledoc.http.NetworkErrors;
import io.whaledoc.http.MessageListener;
import io.whaledoc.http.Reconnect;
import io.whaledoc.http.WebSocketClient;
import io.whaledoc.http.WebSocketConnection;
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
    private static final String TYPE = "type";
    private static final String EVENT_TYPE = "event";
    private static final String ID = "id";
    /** The headers a webhook delivery carries; anything else WhaleDoc sent is left out. */
    private static final Set<String> FORWARDED_HEADERS = Set.of("content-type", "user-agent");
    private static final Set<String> FORWARDED_HEADER_PREFIXES = Set.of("webhook-", "whaledoc-");
    /** Closing the forward's answer ends a read still waiting here. */
    private static final ExecutorService BODY_READERS = Executors.newVirtualThreadPerTaskExecutor();

    private final ApiClient apiClient;
    private final WebSocketClient webSocketClient;
    private final HttpClient forwardClient;
    private final ObjectMapper objectMapper;

    /** {@code forwardClient} follows no redirects: a redirect is the local endpoint's answer, reported as it is. */
    public WebhookClient(ApiClient apiClient, WebSocketClient webSocketClient, HttpClient forwardClient,
                         ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.webSocketClient = webSocketClient;
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

    /** An {@code event} message: the event's type and id, with the request WhaleDoc signed for it. */
    public SignedEvent parse(ListenMessage event) {

        JsonNode message = readTree(event.json());
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

    /** A field of a message about the connection itself, such as the signing secret of {@code ready}. */
    public Optional<String> field(ListenMessage message, String name) {

        return Optional.ofNullable(readTree(message.json()).get(name)).flatMap(WebhookClient::text);
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

    /** The {@code answer} message that tells WhaleDoc what the local endpoint answered, for the app's Events tab. */
    public String answerMessage(URI target, SignedEvent event, ForwardResult result) {

        try {
            return objectMapper.writeValueAsString(toAnswer(target, event, result));
        } catch (JsonProcessingException e) {
            throw new ApiException("The CLI couldn't write the answer for WhaleDoc.", e);
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

    /**
     * Opens the connection. Messages about the connection itself always come through; events only when {@code events}
     * names them or is {@link WebhookEvents#ALL}.
     */
    public WebSocketConnection listen(String accessToken, Set<String> events, ListenListener listener) {

        return connect(accessToken, "", events, listener);
    }

    /** As {@link #listen(String, Set, ListenListener)}, telling WhaleDoc where events are forwarded, for the app to show. */
    public WebSocketConnection listen(String accessToken, URI forwardTo, Set<String> events, ListenListener listener) {

        String query = FORWARD_TO_PARAMETER + URLEncoder.encode(forwardTo.toString(), StandardCharsets.UTF_8);
        return connect(accessToken, query, events, listener);
    }

    private WebSocketConnection connect(String accessToken, String query, Set<String> events, ListenListener listener) {

        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken is required");
        }

        Objects.requireNonNull(events);
        Objects.requireNonNull(listener);

        URI uri = URI.create(webSocketUrl(apiClient.baseUrl()) + ApiConstants.WEBHOOK_EVENTS + query);
        return webSocketClient.connect(uri, accessToken, new MessageListener() {

            @Override
            public void onMessage(String message) {
                readMessage(message).filter(read -> isWanted(read, events)).ifPresent(listener::onMessage);
            }

            @Override
            public void onReconnecting(Reconnect reconnect) {
                listener.onReconnecting(reconnect);
            }

            @Override
            public void onReconnected() {
                listener.onReconnected();
            }
        });
    }

    /** The API's address with the WebSocket scheme: {@code https} becomes {@code wss}, {@code http} becomes {@code ws}. */
    private static String webSocketUrl(String baseUrl) {

        return baseUrl.replaceFirst("^http", "ws");
    }

    /** Nothing for a message that isn't a JSON object: it is logged and skipped, and listening goes on. */
    private Optional<ListenMessage> readMessage(String message) {

        try {
            JsonNode json = objectMapper.readTree(message);
            return Optional.of(new ListenMessage(textOf(json, TYPE), textOf(json, EVENT_TYPE), textOf(json, ID), message));
        } catch (JsonProcessingException e) {
            log.warn("Unreadable message from WhaleDoc skipped", e);
            return Optional.empty();
        }
    }

    private static String textOf(JsonNode json, String field) {

        return Optional.ofNullable(json.get(field)).flatMap(WebhookClient::text).orElse(null);
    }

    private static boolean isWanted(ListenMessage message, Set<String> events) {

        return !message.isEvent() || events.contains(WebhookEvents.ALL) || events.contains(message.event());
    }

    private static AnswerMessage toAnswer(URI target, SignedEvent event, ForwardResult result) {

        return switch (result) {
            case ForwardResult.Answered answered -> new AnswerMessage(WebhookEvents.ANSWER, event.id(),
                    target.toString(), answered.status(), answered.durationMs(),
                    StringUtils.truncate(answered.body(), REPORTED_BODY_LENGTH), null);
            case ForwardResult.Unanswered unanswered -> new AnswerMessage(WebhookEvents.ANSWER, event.id(),
                    target.toString(), null, unanswered.durationMs(), null, unanswered.kind().name());
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

    /** The {@code answer} message's JSON: absent values are {@code null}, as WhaleDoc expects. */
    private record AnswerMessage(String type, String eventId, String forwardUrl, Integer status, long durationMs,
                                 String body, String failure) {
    }
}
