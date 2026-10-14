package io.whaledoc.commands.workspace;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.TestConsole;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.workspace.Workspace;
import io.whaledoc.workspace.WorkspaceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WorkspaceCommandsTest {

    private static final String ACCESS_TOKEN = "wd_cli_token";
    private static final Workspace ACME = new Workspace("0192f4c1-acme", "acme-hr", "Acme HR", true);
    private static final Workspace NORTHWIND = new Workspace("0192f4c2-north", "northwind", "Northwind", false);
    private static final Workspace OTHER_ACME = new Workspace("0192f4c3-acme2", "acme-hr-2", "Acme HR", false);

    @Mock
    private ConfigManager configManager;

    @Mock
    private WorkspaceClient workspaceClient;

    private final TestConsole testConsole = TestConsole.plain();

    @Test
    void shouldPrintTableWithCurrentWorkspaceMarkedWhenListing() {

        // given
        givenLoggedIn();
        given(workspaceClient.list(ACCESS_TOKEN)).willReturn(List.of(ACME, NORTHWIND));

        // when
        int exitCode = new CommandLine(new WorkspaceListCommand(configManager, workspaceClient, testConsole.console())).execute();

        // then
        assertThat(exitCode).isZero();
        assertThat(testConsole.output().lines().toList()).containsExactly("*	Acme HR	acme-hr", "	Northwind	northwind");
        assertThat(testConsole.errors()).isEqualToIgnoringNewLines("Loading workspaces...");
    }

    @Test
    void shouldSwitchByNameWhenOnlyOneWorkspaceHasIt() {

        // given
        givenLoggedIn();
        given(workspaceClient.list(ACCESS_TOKEN)).willReturn(List.of(ACME, NORTHWIND));
        given(workspaceClient.switchTo(ACCESS_TOKEN, NORTHWIND.id()))
                .willReturn(new Workspace(NORTHWIND.id(), NORTHWIND.slug(), NORTHWIND.name(), true));

        // when
        int exitCode = switchCommand().execute("Northwind");

        // then
        assertThat(exitCode).isZero();
        assertThat(testConsole.output()).contains("Now working in Northwind");
    }

    @Test
    void shouldSwitchBySlugWhenNamesAreShared() {

        // given
        givenLoggedIn();
        given(workspaceClient.list(ACCESS_TOKEN)).willReturn(List.of(ACME, OTHER_ACME));
        given(workspaceClient.switchTo(ACCESS_TOKEN, OTHER_ACME.id())).willReturn(OTHER_ACME);

        // when
        int exitCode = switchCommand().execute(OTHER_ACME.slug());

        // then
        assertThat(exitCode).isZero();
        then(workspaceClient).should().switchTo(ACCESS_TOKEN, OTHER_ACME.id());
    }

    @Test
    void shouldAskForSlugWithoutSwitchingWhenNameIsShared() {

        // given
        givenLoggedIn();
        given(workspaceClient.list(ACCESS_TOKEN)).willReturn(List.of(ACME, OTHER_ACME));

        // when
        int exitCode = switchCommand().execute("Acme HR");

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).contains("More than one workspace is called Acme HR. Switch by its slug:");
        assertThat(testConsole.output()).contains("acme-hr").contains("acme-hr-2");
        then(workspaceClient).should(never()).switchTo(anyString(), anyString());
    }

    @Test
    void shouldRefuseSwitchWhenNoWorkspaceMatches() {

        // given
        givenLoggedIn();
        given(workspaceClient.list(ACCESS_TOKEN)).willReturn(List.of(ACME));

        // when
        int exitCode = switchCommand().execute("Globex");

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).contains("No workspace you can use is called Globex.");
        then(workspaceClient).should(never()).switchTo(anyString(), anyString());
    }

    @Test
    void shouldShowApiMessageWhenCurrentWorkspaceCanNoLongerBeUsed() {

        // given
        givenLoggedIn();
        given(workspaceClient.current(ACCESS_TOKEN)).willThrow(new ApiException(403,
                "You can no longer use the CLI in Acme HR. Run whaledoc workspace switch to pick another."));

        // when
        int exitCode = new CommandLine(new WorkspaceCurrentCommand(configManager, workspaceClient, testConsole.console())).execute();

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).contains("You can no longer use the CLI in Acme HR.");
    }

    @Test
    void shouldAskToLogInWhenNotLoggedIn() {

        // given
        given(configManager.load()).willReturn(WhaleDocConfig.builder().cliId("cli-1").build());

        // when
        int exitCode = new CommandLine(new WorkspaceListCommand(configManager, workspaceClient, testConsole.console())).execute();

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).contains("You're not logged in.");
        then(workspaceClient).should(never()).list(any());
    }

    private CommandLine switchCommand() {

        return new CommandLine(new WorkspaceSwitchCommand(configManager, workspaceClient, testConsole.console()));
    }

    private void givenLoggedIn() {

        given(configManager.load()).willReturn(WhaleDocConfig.builder().cliId("cli-1").accessToken(ACCESS_TOKEN).build());
    }
}
