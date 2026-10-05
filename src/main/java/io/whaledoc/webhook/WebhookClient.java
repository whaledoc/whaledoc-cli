package io.whaledoc.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.whaledoc.http.ApiClient;
import io.whaledoc.http.ApiConstants;
import io.whaledoc.http.SseClient;
import io.whaledoc.http.SseConnection;
import io.whaledoc.http.SseEvent;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class WebhookClient {

    private final ApiClient apiClient;
    private final SseClient sseClient;
    private final HttpClient httpClient;

    public WebhookClient() {
        this(new ApiClient(new ObjectMapper()), new SseClient(), HttpClient.newHttpClient());
    }

    public WebhookClient(ApiClient apiClient, SseClient sseClient, HttpClient httpClient) {
        this.apiClient = apiClient;
        this.sseClient = sseClient;
        this.httpClient = httpClient;
    }

    public void forward(String url, SseEvent event) {

        if (StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("forward URL is required");
        }

        Objects.requireNonNull(event);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(event.data()))
                .build();

        try {
            HttpResponse<Void> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException(
                        "Forwarding webhook event failed with status " + response.statusCode()
                );
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Forwarding webhook event was interrupted.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to forward webhook event.", e);
        }
    }


    public SseConnection listen(String accessToken, Set<String> events, Consumer<SseEvent> eventConsumer) {

        if (StringUtils.isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken is required");
        }

        Objects.requireNonNull(events);
        Objects.requireNonNull(eventConsumer);

        URI uri = URI.create(
                apiClient.baseUrl() + ApiConstants.WEBHOOK_EVENTS
        );

        return sseClient.connect(uri, accessToken, event -> {
            if (events.contains("*") || events.contains(event.event())) {
                eventConsumer.accept(event);
            }
        });
    }
}
