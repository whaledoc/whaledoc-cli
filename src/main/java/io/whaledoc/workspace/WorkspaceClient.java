package io.whaledoc.workspace;

import io.whaledoc.http.ApiClient;
import io.whaledoc.http.ApiConstants;

import java.util.List;

/** The workspaces your login may use, and the one the CLI works in now. */
public final class WorkspaceClient {

    private final ApiClient apiClient;

    public WorkspaceClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public List<Workspace> list(String accessToken) {

        return List.of(apiClient.getAuthorized(ApiConstants.WORKSPACES, accessToken, Workspace[].class));
    }

    public Workspace current(String accessToken) {

        return apiClient.getAuthorized(ApiConstants.CURRENT_WORKSPACE, accessToken, Workspace.class);
    }

    public Workspace switchTo(String accessToken, String workspaceId) {

        return apiClient.putAuthorized(ApiConstants.CURRENT_WORKSPACE, accessToken, new SwitchRequest(workspaceId),
                Workspace.class);
    }

    private record SwitchRequest(String workspaceId) {
    }
}
