package io.whaledoc.commands.auth;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.auth.CurrentLogin;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.TestConsole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class StatusCommandTest {

    private static final String CLI_ID = "3f1b2c4d-5e6f-4a1b-9c2d-7e8f9a0b1c2d";
    private static final String ACCESS_TOKEN = "access-token";
    private static final CurrentLogin.Account ACCOUNT = new CurrentLogin.Account("mara@acme.com", "Mara Jansen");

    @Mock
    private ConfigManager configManager;

    @Mock
    private AuthClient authClient;

    private final TestConsole testConsole = TestConsole.plain();

    private StatusCommand statusCommand;

    @BeforeEach
    void setUp() {
        statusCommand = new StatusCommand(configManager, authClient, testConsole.console());
    }

    @Test
    void shouldShowAccountWorkspaceAndDeviceWhenLoggedIn() {

        // given
        givenLoggedIn();
        given(authClient.currentLogin(ACCESS_TOKEN))
                .willReturn(Optional.of(new CurrentLogin(ACCOUNT, "dev-laptop", new CurrentLogin.Workspace("acme-hr", "Acme HR"))));

        // when
        Integer exitCode = statusCommand.call();

        // then
        assertThat(exitCode).isZero();
        assertThat(testConsole.output().lines().toList()).containsExactly(
                "Logged in as mara@acme.com (Mara Jansen)",
                "Workspace  Acme HR  acme-hr",
                "Device     dev-laptop");
    }

    @Test
    void shouldSayHowToPickWorkspaceWhenLoginHasNone() {

        // given
        givenLoggedIn();
        given(authClient.currentLogin(ACCESS_TOKEN)).willReturn(Optional.of(new CurrentLogin(ACCOUNT, "dev-laptop", null)));

        // when
        Integer exitCode = statusCommand.call();

        // then
        assertThat(exitCode).isZero();
        assertThat(testConsole.output()).contains("Workspace  none. Run whaledoc workspace switch to pick one.");
    }

    @Test
    void shouldAskToLogInAgainWhenSavedLoginIsRefused() {

        // given
        givenLoggedIn();
        given(authClient.currentLogin(ACCESS_TOKEN)).willReturn(Optional.empty());

        // when
        Integer exitCode = statusCommand.call();

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).contains("Your login is no longer valid. Run whaledoc login to log in again.");
    }

    @Test
    void shouldSayNotLoggedInWithoutCallingApiWhenNoLoginIsSaved() {

        // given
        given(configManager.load()).willReturn(WhaleDocConfig.builder().cliId(CLI_ID).build());

        // when
        Integer exitCode = statusCommand.call();

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).contains("You're not logged in. Run whaledoc login to log in.");
        then(authClient).shouldHaveNoInteractions();
    }

    private void givenLoggedIn() {

        given(configManager.load()).willReturn(WhaleDocConfig.builder().cliId(CLI_ID).accessToken(ACCESS_TOKEN).build());
    }
}
