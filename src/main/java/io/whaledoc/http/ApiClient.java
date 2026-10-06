package io.whaledoc.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.whaledoc.exceptions.ApiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls the WhaleDoc REST API. Every request carries the API version and expects a JSON response.
 */
public final class ApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public ApiClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public <T> T post(String path, Object requestBody, Class<T> responseType) {

        try {
            HttpRequest request = newRequest(path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            return objectMapper.readValue(send(request), responseType);

        } catch (IOException e) {
            throw new ApiException("Unable to process the API response.", e);
        }
    }

    public void postAuthorized(String path, String accessToken) {

        HttpRequest request = newRequest(path)
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        send(request);
    }

    public String baseUrl() {
        return baseUrl;
    }

    private HttpRequest.Builder newRequest(String path) {

        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("X-API-Version", ApiConstants.API_VERSION)
                .header("Accept", "application/json");
    }

    private String send(HttpRequest request) {

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(response.statusCode(), response.body());
            }

            return response.body();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("HTTP request was interrupted.", e);

        } catch (IOException e) {
            throw new ApiException(NetworkErrors.describe(request.uri(), e), e);
        }
    }
}
