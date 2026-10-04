package io.whaledoc.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.whaledoc.http.ApiClient;
import io.whaledoc.http.ApiConstants;
import io.whaledoc.http.SseClient;
import io.whaledoc.http.SseConnection;
import io.whaledoc.http.SseEvent;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class WebhookClient {

    private final ApiClient apiClient;
    private final SseClient sseClient;

    public WebhookClient() {
        this.apiClient = new ApiClient(new ObjectMapper());
        this.sseClient = new SseClient();
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
