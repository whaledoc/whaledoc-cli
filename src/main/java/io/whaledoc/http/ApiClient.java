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
    private static final String POST = "POST";
    private static final String PUT = "PUT";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public ApiClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public <T> T post(String path, Object requestBody, Class<T> responseType) {

        return sendJson(newRequest(path), POST, requestBody, responseType);
    }

    public <T> T postAuthorized(String path, String accessToken, Object requestBody, Class<T> responseType) {

        return sendJson(authorizedRequest(path, accessToken), POST, requestBody, responseType);
    }

    public void postAuthorized(String path, String accessToken) {

        send(authorizedRequest(path, accessToken).POST(HttpRequest.BodyPublishers.noBody()).build());
    }

    /** A JSON request whose answer has no body to read, such as 204 No Content. */
    public void postAuthorizedWithoutAnswer(String path, String accessToken, Object requestBody) {

        try {
            send(authorizedRequest(path, accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build());

        } catch (IOException e) {
            throw new ApiException("Unable to send the request.", e);
        }
    }

    public <T> T getAuthorized(String path, String accessToken, Class<T> responseType) {

        return read(send(authorizedRequest(path, accessToken).GET().build()), responseType);
    }

    public <T> T putAuthorized(String path, String accessToken, Object requestBody, Class<T> responseType) {

        return sendJson(authorizedRequest(path, accessToken), PUT, requestBody, responseType);
    }

    public String baseUrl() {
        return baseUrl;
    }

    private <T> T sendJson(HttpRequest.Builder request, String method, Object requestBody, Class<T> responseType) {

        try {
            HttpRequest jsonRequest = request
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            return read(send(jsonRequest), responseType);

        } catch (IOException e) {
            throw new ApiException("Unable to process the API response.", e);
        }
    }

    private <T> T read(String body, Class<T> responseType) {

        try {
            return objectMapper.readValue(body, responseType);

        } catch (IOException e) {
            throw new ApiException("Unable to process the API response.", e);
        }
    }

    private HttpRequest.Builder authorizedRequest(String path, String accessToken) {

        return newRequest(path).header("Authorization", "Bearer " + accessToken);
    }

    private HttpRequest.Builder newRequest(String path) {

        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header(ApiConstants.API_VERSION_HEADER, ApiConstants.API_VERSION)
                .header("Accept", "application/json");
    }

    private String send(HttpRequest request) {

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw ErrorResponses.toException(response.statusCode(), response.body(), response.headers());
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
