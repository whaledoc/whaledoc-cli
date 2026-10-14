package io.whaledoc.commands.workspace;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
        name = "workspace",
        mixinStandardHelpOptions = true,
        description = "List and switch the workspaces the CLI works in.",
        header = {
                "List and switch the workspaces the CLI works in.",
                "%nYour login can use every workspace where you may sign in the CLI. listen and",
                "trigger work in the current one.",
                """

                         @|bold Examples:|@
                            whaledoc workspace list
                            whaledoc workspace current
                            whaledoc workspace switch "Acme HR"
                        """
        },
        headerHeading = "",
        synopsisHeading = "%nUsage:%n  ",
        commandListHeading = "%nCommands:%n",
        optionListHeading = "%nFlags:%n",
        customSynopsis = {
                "whaledoc workspace [command]"
        },
        subcommands = {
                WorkspaceListCommand.class,
                WorkspaceCurrentCommand.class,
                WorkspaceSwitchCommand.class
        }
)
public class WorkspaceCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
