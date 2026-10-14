package io.whaledoc.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.whaledoc.http.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class WorkspaceClientTest {

    private static final String ACCESS_TOKEN = "wd_cli_token";
    private static final Workspace ACME = new Workspace("ws-1", "acme-hr", "Acme HR", true);
    private static final Workspace NORTHWIND = new Workspace("ws-2", "northwind", "Northwind", false);

    private WorkspaceClient workspaceClient;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {

        ApiClient apiClient = new ApiClient(HttpClient.newHttpClient(), new ObjectMapper(), wireMock.getHttpBaseUrl());
        workspaceClient = new WorkspaceClient(apiClient);
    }

    @Test
    void shouldReturnWorkspacesWhenApiListsThem() {

        // given
        stubFor(get(urlEqualTo("/cli/workspaces"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
                .willReturn(okJson("""
                        [{"id": "ws-1", "slug": "acme-hr", "name": "Acme HR", "current": true},
                         {"id": "ws-2", "slug": "northwind", "name": "Northwind", "current": false}]
                        """)));

        // when
        List<Workspace> workspaces = workspaceClient.list(ACCESS_TOKEN);

        // then
        assertThat(workspaces).containsExactly(ACME, NORTHWIND);
    }

    @Test
    void shouldSendWorkspaceIdWhenSwitching() {

        // given
        stubFor(put(urlEqualTo("/cli/workspaces/current"))
                .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
                .withRequestBody(equalToJson("""
                        {"workspaceId": "ws-2"}
                        """))
                .willReturn(okJson("""
                        {"id": "ws-2", "slug": "northwind", "name": "Northwind", "current": true}
                        """)));

        // when
        Workspace switched = workspaceClient.switchTo(ACCESS_TOKEN, "ws-2");

        // then
        assertThat(switched).isEqualTo(new Workspace("ws-2", "northwind", "Northwind", true));
    }
}
