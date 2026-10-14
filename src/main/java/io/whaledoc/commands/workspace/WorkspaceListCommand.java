package io.whaledoc.commands.workspace;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.workspace.Workspace;
import io.whaledoc.workspace.WorkspaceClient;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@Slf4j
@Command(
        name = "list",
        mixinStandardHelpOptions = true,
        description = "List the workspaces you can use; * marks the current one."
)
public class WorkspaceListCommand implements Callable<Integer> {

    private final ConfigManager configManager;
    private final WorkspaceClient workspaceClient;
    private final Console console;

    public WorkspaceListCommand(ConfigManager configManager, WorkspaceClient workspaceClient, Console console) {
        this.configManager = configManager;
        this.workspaceClient = workspaceClient;
        this.console = console;
    }

    @Override
    public Integer call() {

        Optional<String> token = WorkspaceCommands.accessToken(configManager, console);

        if (token.isEmpty()) {
            return 1;
        }

        Spinner spinner = console.spinner("Loading workspaces...");
        spinner.start();

        try {
            List<Workspace> workspaces = workspaceClient.list(token.get());
            spinner.stop();
            printWorkspaces(workspaces);
            return 0;

        } catch (ApiException e) {
            spinner.stop();
            log.error("Listing workspaces failed", e);
            console.error(WorkspaceCommands.describe(e, console));
            return 1;
        }
    }

    private void printWorkspaces(List<Workspace> workspaces) {

        if (workspaces.isEmpty()) {
            console.println("You can't use the CLI in any workspace. Ask an owner or admin for a role other than Viewer.");
            return;
        }

        WorkspaceTable.print(workspaces, console);
    }
}
