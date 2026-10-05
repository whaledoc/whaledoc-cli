package io.whaledoc.commands.auth;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.TestConsole;
import io.whaledoc.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LogoutCommandTest {

    private static final String CLI_ID = "3f1b2c4d-5e6f-4a1b-9c2d-7e8f9a0b1c2d";
    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private ConfigManager configManager;

    @Mock
    private AuthClient authClient;

    private final TestConsole testConsole = TestConsole.plain();

    private LogoutCommand logoutCommand;

    @BeforeEach
    void setUp() {
        logoutCommand = new LogoutCommand(configManager, authClient, testConsole.console());
    }

    @Test
    void shouldSucceedWithoutCallingApiWhenUserIsAlreadyLoggedOut() {

        // given
        given(configManager.load()).willReturn(createLoggedOutConfig());

        // when
        Integer exitCode = logoutCommand.call();

        // then
        assertThat(exitCode).isZero();
        then(authClient).shouldHaveNoInteractions();
        then(configManager).should(never()).saveToFile(any());
    }

    @Test
    void shouldRemoveAccessTokenWhenLogoutSucceeds() {

        // given
        given(configManager.load()).willReturn(createLoggedInConfig());
        WhaleDocConfig expectedConfig = createLoggedOutConfig();
        ArgumentCaptor<WhaleDocConfig> savedConfig = ArgumentCaptor.forClass(WhaleDocConfig.class);

        // when
        Integer exitCode = logoutCommand.call();

        // then
        assertThat(exitCode).isZero();
        then(authClient).should().logout(ACCESS_TOKEN);
        then(configManager).should().saveToFile(savedConfig.capture());
        assertThat(savedConfig.getValue()).usingRecursiveComparison().isEqualTo(expectedConfig);
    }

    @Test
    void shouldKeepAccessTokenWhenLogoutFails() {

        // given
        given(configManager.load()).willReturn(createLoggedInConfig());
        willThrow(new ApiException(500, "Internal Server Error")).given(authClient).logout(ACCESS_TOKEN);

        // when
        Integer exitCode = logoutCommand.call();

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).isEqualToIgnoringNewLines("x Logout failed: API request failed with status 500: Internal Server Error");
        then(configManager).should(never()).saveToFile(any());
    }

    private WhaleDocConfig createLoggedInConfig() {

        return WhaleDocConfig.builder()
                .cliId(CLI_ID)
                .accessToken(ACCESS_TOKEN)
                .build();
    }

    private WhaleDocConfig createLoggedOutConfig() {

        return WhaleDocConfig.builder()
                .cliId(CLI_ID)
                .build();
    }
}
