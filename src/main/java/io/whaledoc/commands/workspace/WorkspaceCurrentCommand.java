package io.whaledoc.commands.workspace;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.workspace.Workspace;
import io.whaledoc.workspace.WorkspaceClient;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;

import java.util.Optional;
import java.util.concurrent.Callable;

@Slf4j
@Command(
        name = "current",
        mixinStandardHelpOptions = true,
        description = "Show the workspace the CLI works in."
)
public class WorkspaceCurrentCommand implements Callable<Integer> {

    private final ConfigManager configManager;
    private final WorkspaceClient workspaceClient;
    private final Console console;

    public WorkspaceCurrentCommand(ConfigManager configManager, WorkspaceClient workspaceClient, Console console) {
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

        Spinner spinner = console.spinner("Loading the current workspace...");
        spinner.start();

        try {
            Workspace workspace = workspaceClient.current(token.get());
            spinner.stop();
            console.println(console.bold(workspace.name()) + "  " + console.dim(workspace.slug()));
            return 0;

        } catch (ApiException e) {
            spinner.stop();
            log.error("Reading the current workspace failed", e);
            console.error(WorkspaceCommands.describe(e, console));
            return 1;
        }
    }
}
