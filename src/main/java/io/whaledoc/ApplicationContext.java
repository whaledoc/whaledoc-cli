package io.whaledoc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.whaledoc.auth.AuthClient;
import io.whaledoc.config.ApplicationConfig;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.console.Console;
import io.whaledoc.http.ApiClient;
import io.whaledoc.http.SseClient;
import io.whaledoc.update.UpdateService;
import io.whaledoc.webhook.WebhookClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The objects the commands share during one run of the CLI, created once at startup:
 * the terminal console, a single HTTP client, a single JSON mapper and the clients built on top of them.
 */
public record ApplicationContext(
        ApplicationConfig config,
        Console console,
        ConfigManager configManager,
        AuthClient authClient,
        WebhookClient webhookClient,
        UpdateService updateService
) {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    public static ApplicationContext create() {

        ApplicationConfig config = ApplicationConfig.load();
        HttpClient httpClient = createHttpClient();
        ObjectMapper objectMapper = createObjectMapper();

        ApiClient apiClient = new ApiClient(httpClient, objectMapper, config.apiUrl());
        SseClient sseClient = new SseClient(httpClient);

        return new ApplicationContext(
                config,
                Console.system(),
                ConfigManager.forCurrentUser(objectMapper),
                new AuthClient(apiClient, sseClient, objectMapper),
                new WebhookClient(apiClient, sseClient, httpClient),
                UpdateService.create(httpClient, config.releasesUrl())
        );
    }

    // Follows redirects for release downloads, which GitHub serves from its file storage
    private static HttpClient createHttpClient() {

        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    // Ignores unknown fields so older CLI versions keep working when the API adds new ones
    private static ObjectMapper createObjectMapper() {

        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
