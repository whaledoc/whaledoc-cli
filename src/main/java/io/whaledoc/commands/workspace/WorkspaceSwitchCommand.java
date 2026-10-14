package io.whaledoc.commands.workspace;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.workspace.Workspace;
import io.whaledoc.workspace.WorkspaceClient;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@Slf4j
@Command(
        name = "switch",
        mixinStandardHelpOptions = true,
        description = "Work in another workspace. Give its slug, or its name when no other workspace has it.",
        customSynopsis = {
                "whaledoc workspace switch <workspace>"
        },
        parameterListHeading = "%nArguments:%n"
)
public class WorkspaceSwitchCommand implements Callable<Integer> {

    private final ConfigManager configManager;
    private final WorkspaceClient workspaceClient;
    private final Console console;

    @Parameters(index = "0", paramLabel = "<workspace>", description = "The workspace's slug or name, as whaledoc workspace list shows them.")
    private String workspace;

    public WorkspaceSwitchCommand(ConfigManager configManager, WorkspaceClient workspaceClient, Console console) {
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

        Spinner spinner = console.spinner("Switching to " + workspace + "...");
        spinner.start();

        try {
            List<Workspace> workspaces = workspaceClient.list(token.get());
            return switchTo(token.get(), workspaces, spinner);

        } catch (ApiException e) {
            spinner.stop();
            log.error("Switching workspace failed", e);
            console.error(WorkspaceCommands.describe(e, console));
            return 1;
        }
    }

    private int switchTo(String token, List<Workspace> workspaces, Spinner spinner) {

        List<Workspace> matches = matching(workspaces);

        if (matches.size() != 1) {
            spinner.stop();
        }

        if (matches.isEmpty()) {
            console.error("No workspace you can use is called " + workspace + ". Run "
                    + console.bold("whaledoc workspace list") + " to see them.");
            return 1;
        }

        if (matches.size() > 1) {
            console.error("More than one workspace is called " + workspace + ". Switch by its slug:");
            WorkspaceTable.print(matches, console);
            return 1;
        }

        Workspace match = matches.getFirst();

        // The switch is idempotent, but saying so is more honest than "Now working in" without a change
        if (match.current()) {

            spinner.stop();
            console.success("Already working in " + console.bold(match.name()) + ".");
            return 0;
        }

        Workspace switched = workspaceClient.switchTo(token, match.id());
        spinner.stop();
        console.success("Now working in " + console.bold(switched.name()));
        return 0;
    }

    /** The workspaces with this slug or this name, ignoring case; more than one means the name is shared. */
    private List<Workspace> matching(List<Workspace> workspaces) {

        return workspaces.stream()
                .filter(candidate -> candidate.slug().equalsIgnoreCase(workspace) || candidate.name().equalsIgnoreCase(workspace))
                .toList();
    }
}
