package io.whaledoc.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.whaledoc.exceptions.ApiException;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@WireMockTest
class ApiClientTest {

    @Test
    void shouldReturnResponseWhenPostSucceeds(WireMockRuntimeInfo wireMock) {

        // given
        givenDocumentCanBeCreated();
        ApiClient apiClient = createApiClient(wireMock);
        DocumentResponse expectedResponse = new DocumentResponse("doc-1", "Invoice");

        // when
        DocumentResponse actualResponse = apiClient.post("/documents", new DocumentRequest("Invoice"), DocumentResponse.class);

        // then
        assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    }

    @Test
    void shouldThrowApiExceptionWhenServerRespondsWithError(WireMockRuntimeInfo wireMock) {

        // given
        stubFor(post(urlEqualTo("/documents")).willReturn(serverError().withBody("Something went wrong")));
        ApiClient apiClient = createApiClient(wireMock);

        // when
        Throwable thrown = catchThrowable(() -> apiClient.post("/documents", new DocumentRequest("Invoice"), DocumentResponse.class));

        // then
        assertThat(thrown)
                .isInstanceOf(ApiException.class)
                .hasMessage("API request failed with status 500: Something went wrong")
                .extracting(exception -> ((ApiException) exception).statusCode())
                .isEqualTo(500);
    }

    @Test
    void shouldSendBearerTokenWhenRequestHasAccessToken(WireMockRuntimeInfo wireMock) {

        // given
        givenEndpointRequiresAccessToken("access-token");
        ApiClient apiClient = createApiClient(wireMock);

        // when
        Throwable thrown = catchThrowable(() -> apiClient.post("/cli/auth/logout", "access-token"));

        // then
        assertThat(thrown).isNull();
    }

    private void givenDocumentCanBeCreated() {

        stubFor(post(urlEqualTo("/documents"))
                .withHeader("X-API-Version", equalTo(ApiConstants.API_VERSION))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        { "name": "Invoice" }
                        """))
                .willReturn(okJson("""
                        { "id": "doc-1", "name": "Invoice" }
                        """)));
    }

    private void givenEndpointRequiresAccessToken(String accessToken) {

        stubFor(any(urlEqualTo("/cli/auth/logout"))
                .withHeader("Authorization", equalTo("Bearer " + accessToken))
                .withHeader("X-API-Version", equalTo(ApiConstants.API_VERSION))
                .willReturn(noContent()));
    }

    private ApiClient createApiClient(WireMockRuntimeInfo wireMock) {
        return new ApiClient(HttpClient.newHttpClient(), new ObjectMapper(), wireMock.getHttpBaseUrl());
    }

    private record DocumentRequest(String name) {
    }

    private record DocumentResponse(String id, String name) {
    }
}
