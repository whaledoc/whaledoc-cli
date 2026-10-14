package io.whaledoc.commands.auth;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.auth.AuthSession;
import io.whaledoc.auth.AuthenticationEvent;
import io.whaledoc.auth.CurrentLogin;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.TestConsole;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.utility.BrowserLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LoginCommandTest {

    private static final String CLI_ID = "3f1b2c4d-5e6f-4a1b-9c2d-7e8f9a0b1c2d";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String SAVED_TOKEN = "saved-token";
    private static final AuthenticationEvent APPROVED = new AuthenticationEvent(ACCESS_TOKEN, "mara@acme.com", "Acme");
    private static final CurrentLogin CURRENT_LOGIN = new CurrentLogin(
            new CurrentLogin.Account("mara@acme.com", "Mara Jansen"), "dev-laptop", new CurrentLogin.Workspace("acme-hr", "Acme HR"));
    private static final URI AUTHORIZATION_URL = URI.create("https://app.whaledoc.io/cli/authorize/session-1");
    private static final AuthSession SESSION = new AuthSession("session-1", AUTHORIZATION_URL, "ABCD-1234");

    @Mock
    private ConfigManager configManager;

    @Mock
    private AuthClient authClient;

    @Mock
    private BrowserLauncher browserLauncher;

    private TestConsole testConsole;

    @Test
    void shouldSaveAccessTokenWhenLoginIsApproved() {

        // given
        givenLoginSessionIsCreated();
        given(authClient.waitForAuthentication(SESSION)).willReturn(CompletableFuture.completedFuture(APPROVED));
        WhaleDocConfig expectedConfig = createConfig(ACCESS_TOKEN);
        ArgumentCaptor<WhaleDocConfig> savedConfig = ArgumentCaptor.forClass(WhaleDocConfig.class);

        // when
        Integer exitCode = createLoginCommand("").call();

        // then
        assertThat(exitCode).isZero();
        then(configManager).should().saveToFile(savedConfig.capture());
        assertThat(savedConfig.getValue()).usingRecursiveComparison().isEqualTo(expectedConfig);
        assertThat(testConsole.output()).contains("Logged in as mara@acme.com. Working in Acme.");
    }

    @Test
    void shouldOpenBrowserWhenUserPressesEnter() {

        // given
        givenLoginSessionIsCreated();
        CompletableFuture<AuthenticationEvent> authentication = givenLoginIsApprovedOnceBrowserOpens();

        // when
        Integer exitCode = createLoginCommand("\n").call();

        // then
        assertThat(exitCode).isZero();
        assertThat(authentication).isCompletedWithValue(APPROVED);
        then(browserLauncher).should().open(AUTHORIZATION_URL);
    }

    @Test
    void shouldNotSaveAccessTokenWhenLoginFails() {

        // given
        givenLoginSessionIsCreated();
        given(authClient.waitForAuthentication(SESSION))
                .willReturn(CompletableFuture.failedFuture(new ApiException(404, "Unknown session")));

        // when
        Integer exitCode = createLoginCommand("").call();

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).contains("Login failed: Unknown session");
        then(configManager).should(never()).saveToFile(any());
    }

    @Test
    void shouldNameLoginAfterDeviceNameWhenOneIsGiven() {

        // given
        given(configManager.load()).willReturn(createConfig(null));
        given(authClient.createSession(CLI_ID, Optional.of("ci-runner"))).willReturn(SESSION);
        given(authClient.waitForAuthentication(SESSION)).willReturn(CompletableFuture.completedFuture(APPROVED));
        LoginCommand loginCommand = createLoginCommand("");
        new CommandLine(loginCommand).parseArgs("--device-name", "ci-runner");

        // when
        Integer exitCode = loginCommand.call();

        // then
        assertThat(exitCode).isZero();
        then(authClient).should().createSession(CLI_ID, Optional.of("ci-runner"));
    }

    @Test
    void shouldNotWaitForApprovalWhenSessionCannotBeCreated() {

        // given
        given(configManager.load()).willReturn(createConfig(null));
        given(authClient.createSession(eq(CLI_ID), any())).willThrow(new ApiException("Unable to reach WhaleDoc."));

        // when
        Integer exitCode = createLoginCommand("").call();

        // then
        assertThat(exitCode).isEqualTo(1);
        then(authClient).should(never()).waitForAuthentication(any());
    }

    @Test
    void shouldRefuseLoginWhenSavedLoginIsStillActive() {

        // given
        given(configManager.load()).willReturn(createConfig(SAVED_TOKEN));
        given(authClient.currentLogin(SAVED_TOKEN)).willReturn(Optional.of(CURRENT_LOGIN));

        // when
        Integer exitCode = createLoginCommand("").call();

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).contains("You're already logged in as mara@acme.com, working in Acme HR.");
        assertThat(testConsole.output()).contains("Run whaledoc logout first to log in with another account.");
        then(authClient).should(never()).createSession(any(), any());
    }

    @Test
    void shouldStartNewLoginWhenSavedLoginNoLongerWorks() {

        // given
        given(configManager.load()).willReturn(createConfig(SAVED_TOKEN));
        given(authClient.currentLogin(SAVED_TOKEN)).willReturn(Optional.empty());
        given(authClient.createSession(eq(CLI_ID), any())).willReturn(SESSION);
        given(authClient.waitForAuthentication(SESSION)).willReturn(CompletableFuture.completedFuture(APPROVED));

        // when
        Integer exitCode = createLoginCommand("").call();

        // then
        assertThat(exitCode).isZero();
        assertThat(testConsole.output()).contains("Your previous login no longer works. Starting a new one.");
        then(configManager).should().saveToFile(any());
    }

    @Test
    void shouldNotStartLoginWhenSavedLoginCannotBeChecked() {

        // given
        given(configManager.load()).willReturn(createConfig(SAVED_TOKEN));
        given(authClient.currentLogin(SAVED_TOKEN)).willThrow(new ApiException("Unable to reach WhaleDoc."));

        // when
        Integer exitCode = createLoginCommand("").call();

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).contains("Login failed: Unable to reach WhaleDoc.");
        then(authClient).should(never()).createSession(any(), any());
    }

    private void givenLoginSessionIsCreated() {

        given(configManager.load()).willReturn(createConfig(null));
        given(authClient.createSession(eq(CLI_ID), any())).willReturn(SESSION);
    }

    // The login only completes after the browser was opened, as when the user approves it there
    private CompletableFuture<AuthenticationEvent> givenLoginIsApprovedOnceBrowserOpens() {

        CompletableFuture<AuthenticationEvent> authentication = new CompletableFuture<>();
        given(authClient.waitForAuthentication(SESSION)).willReturn(authentication);
        given(browserLauncher.open(AUTHORIZATION_URL)).willAnswer(invocation -> authentication.complete(APPROVED));

        return authentication;
    }

    private LoginCommand createLoginCommand(String userInput) {

        testConsole = TestConsole.withInput(userInput);
        return new LoginCommand(configManager, authClient, browserLauncher, testConsole.console());
    }

    private WhaleDocConfig createConfig(String accessToken) {

        return WhaleDocConfig.builder()
                .cliId(CLI_ID)
                .accessToken(accessToken)
                .build();
    }
}
