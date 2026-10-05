package io.whaledoc.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.whaledoc.exceptions.ApiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class ApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public ApiClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public <T> T post(
            String path,
            Object requestBody,
            Class<T> responseType
    ) {

        try {
            String body = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("X-API-Version", ApiConstants.API_VERSION)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(response.statusCode(), response.body());
            }

            return objectMapper.readValue(response.body(), responseType);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("HTTP request was interrupted.", e);
        } catch (IOException e) {
            throw new ApiException("HTTP request failed.", e);
        }
    }

    public void post(String path, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("X-API-Version", ApiConstants.API_VERSION)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(response.statusCode(), response.body());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("HTTP request was interrupted.", e);
        } catch (IOException e) {
            throw new ApiException("HTTP request failed.", e);
        }
    }

    public String baseUrl() {
        return baseUrl;
    }
}
