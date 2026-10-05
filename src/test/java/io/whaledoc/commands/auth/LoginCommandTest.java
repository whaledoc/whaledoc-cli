package io.whaledoc.commands.auth;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.auth.AuthSession;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.utility.BrowserLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LoginCommandTest {

    private static final String CLI_ID = "3f1b2c4d-5e6f-4a1b-9c2d-7e8f9a0b1c2d";
    private static final String ACCESS_TOKEN = "access-token";
    private static final URI AUTHORIZATION_URL = URI.create("https://app.whaledoc.io/cli/authorize/session-1");
    private static final AuthSession SESSION = new AuthSession("session-1", AUTHORIZATION_URL, "ABCD-1234");

    @Mock
    private ConfigManager configManager;

    @Mock
    private AuthClient authClient;

    @Mock
    private BrowserLauncher browserLauncher;

    @Test
    void shouldSaveAccessTokenWhenLoginIsApproved() {

        // given
        givenLoginSessionIsCreated();
        given(authClient.waitForAuthentication(SESSION)).willReturn(CompletableFuture.completedFuture(ACCESS_TOKEN));
        WhaleDocConfig expectedConfig = createConfig(ACCESS_TOKEN);
        ArgumentCaptor<WhaleDocConfig> savedConfig = ArgumentCaptor.forClass(WhaleDocConfig.class);

        // when
        Integer exitCode = createLoginCommand("").call();

        // then
        assertThat(exitCode).isZero();
        then(configManager).should().saveToFile(savedConfig.capture());
        assertThat(savedConfig.getValue()).usingRecursiveComparison().isEqualTo(expectedConfig);
    }

    @Test
    void shouldOpenBrowserWhenUserPressesEnter() {

        // given
        givenLoginSessionIsCreated();
        CompletableFuture<String> accessToken = givenLoginIsApprovedOnceBrowserOpens();

        // when
        Integer exitCode = createLoginCommand("\n").call();

        // then
        assertThat(exitCode).isZero();
        assertThat(accessToken).isCompletedWithValue(ACCESS_TOKEN);
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
        then(configManager).should(never()).saveToFile(any());
    }

    @Test
    void shouldNotWaitForApprovalWhenSessionCannotBeCreated() {

        // given
        given(configManager.load()).willReturn(createConfig(null));
        given(authClient.createSession(CLI_ID)).willThrow(new ApiException("Unable to reach WhaleDoc."));

        // when
        Integer exitCode = createLoginCommand("").call();

        // then
        assertThat(exitCode).isEqualTo(1);
        then(authClient).should(never()).waitForAuthentication(any());
    }

    private void givenLoginSessionIsCreated() {

        given(configManager.load()).willReturn(createConfig(null));
        given(authClient.createSession(CLI_ID)).willReturn(SESSION);
    }

    // The login only completes after the browser was opened, as when the user approves it there
    private CompletableFuture<String> givenLoginIsApprovedOnceBrowserOpens() {

        CompletableFuture<String> accessToken = new CompletableFuture<>();
        given(authClient.waitForAuthentication(SESSION)).willReturn(accessToken);
        given(browserLauncher.open(AUTHORIZATION_URL)).willAnswer(invocation -> accessToken.complete(ACCESS_TOKEN));

        return accessToken;
    }

    private LoginCommand createLoginCommand(String userInput) {

        InputStream input = new ByteArrayInputStream(userInput.getBytes(StandardCharsets.UTF_8));
        return new LoginCommand(configManager, authClient, browserLauncher, input);
    }

    private WhaleDocConfig createConfig(String accessToken) {

        return WhaleDocConfig.builder()
                .cliId(CLI_ID)
                .accessToken(accessToken)
                .build();
    }
}
