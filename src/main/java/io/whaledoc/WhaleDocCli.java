package io.whaledoc;

import io.whaledoc.commands.auth.LoginCommand;
import io.whaledoc.commands.auth.LogoutCommand;
import io.whaledoc.commands.webhook.ListenCommand;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.utility.VersionProvider;
import io.whaledoc.config.WhaleDocConfig;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

import static picocli.CommandLine.*;

@Slf4j
@Command(
        name = "whaledoc",
        subcommands = {
                LoginCommand.class,
                LogoutCommand.class,
                ListenCommand.class
        },
        mixinStandardHelpOptions = true,
        header = {
                "The official command-line tool to interact with WhaleDoc."
        },
        headerHeading = "%n",
        customSynopsis = {
                "whaledoc [command]"
        },
        synopsisHeading = "%nUsage:%n  ",
        optionListHeading = "%nFlags:%n",
        commandListHeading = "%nCommands:%n",
        footer = "%nUse \"whaledoc [command] --help\" for more information about a command.",
        versionProvider = VersionProvider.class
)
public class WhaleDocCli implements Runnable {

    @Spec
    CommandSpec spec;

    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Help about any command."
    )
    boolean help;

    @Option(
            names = {"-v", "--version"},
            versionHelp = true,
            description = "Get the version of the WhaleDoc CLI"
    )
    boolean version;

    public static void main(String... args) {

        ConfigManager configManager = new ConfigManager();
        WhaleDocConfig config = configManager.load();

        log.debug("Using CLI ID: {}", config.cliId());

        CommandLine commandLine = new CommandLine(new WhaleDocCli());

        commandLine.setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.ON));

        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}