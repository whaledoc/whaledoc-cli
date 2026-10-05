package io.whaledoc;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.config.ApplicationConfig;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.console.TestConsole;
import io.whaledoc.update.UpdateService;
import io.whaledoc.webhook.WebhookClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CommandFactoryTest {

    @Mock
    private ConfigManager configManager;

    @Mock
    private AuthClient authClient;

    @Mock
    private WebhookClient webhookClient;

    @Mock
    private UpdateService updateService;

    @Test
    void shouldPrintConfiguredVersionWhenVersionIsRequested() {

        // given
        StringWriter output = new StringWriter();
        CommandLine commandLine = createCommandLine(output);

        // when
        int exitCode = commandLine.execute("--version");

        // then
        assertThat(exitCode).isZero();
        assertThat(output.toString()).isEqualToIgnoringNewLines("WhaleDoc version 1.2.3");
    }

    @Test
    void shouldCreateEverySubcommandWhenCommandLineIsBuilt() {

        // given
        StringWriter output = new StringWriter();

        // when
        CommandLine commandLine = createCommandLine(output);

        // then
        assertThat(commandLine.getSubcommands()).containsOnlyKeys("login", "logout", "listen", "update");
    }

    private CommandLine createCommandLine(StringWriter output) {

        ApplicationContext context = new ApplicationContext(
                new ApplicationConfig("https://api.example.com", "https://releases.example.com", "1.2.3"),
                TestConsole.plain().console(),
                configManager,
                authClient,
                webhookClient,
                updateService
        );

        CommandLine commandLine = new CommandLine(new WhaleDocCli(), new CommandFactory(context));
        commandLine.setOut(new PrintWriter(output));

        return commandLine;
    }
}
