package io.whaledoc.commands.webhook;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.TestConsole;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.webhook.TriggeredEvent;
import io.whaledoc.webhook.WebhookClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TriggerCommandTest {

    private static final String CLI_ID = "3f1b2c4d-5e6f-4a1b-9c2d-7e8f9a0b1c2d";
    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private ConfigManager configManager;

    @Mock
    private WebhookClient webhookClient;

    private final TestConsole testConsole = TestConsole.plain();

    private CommandLine commandLine;

    @BeforeEach
    void setUp() {
        commandLine = new CommandLine(new TriggerCommand(configManager, webhookClient, testConsole.console()));
    }

    @Test
    void shouldSendTestEventWhenEventIsKnown() {

        // given
        given(configManager.load()).willReturn(createConfig(ACCESS_TOKEN));
        given(webhookClient.trigger(ACCESS_TOKEN, "job.completed"))
                .willReturn(new TriggeredEvent("evt_01J9Z3K4XR", "job.completed"));

        // when
        int exitCode = commandLine.execute("job.completed");

        // then
        assertThat(exitCode).isZero();
        assertThat(testConsole.output()).contains("Sent job.completed (evt_01J9Z3K4XR)");
    }

    @Test
    void shouldReturnErrorWithoutSendingWhenUserIsNotLoggedIn() {

        // given
        given(configManager.load()).willReturn(createConfig(null));

        // when
        int exitCode = commandLine.execute("job.completed");

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).isEqualToIgnoringNewLines("x You're not logged in. Run whaledoc login first.");
        then(webhookClient).shouldHaveNoInteractions();
    }

    @Test
    void shouldRejectCommandWhenEventIsUnknown() {

        // when
        int exitCode = commandLine.execute("document.created");

        // then
        assertThat(exitCode).isEqualTo(2);
        then(webhookClient).shouldHaveNoInteractions();
    }

    @Test
    void shouldAskToLogInAgainWhenLoginIsNoLongerValid() {

        // given
        given(configManager.load()).willReturn(createConfig(ACCESS_TOKEN));
        given(webhookClient.trigger(ACCESS_TOKEN, "job.failed")).willThrow(new ApiException(401, "Unauthorized"));

        // when
        int exitCode = commandLine.execute("job.failed");

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors())
                .isEqualToIgnoringNewLines("x Your login is no longer valid. Run whaledoc login and try again.");
    }

    @Test
    void shouldShowApiMessageWhenSendingFails() {

        // given
        given(configManager.load()).willReturn(createConfig(ACCESS_TOKEN));
        given(webhookClient.trigger(ACCESS_TOKEN, "job.failed"))
                .willThrow(new ApiException(429, "Too many test events. Try again in a minute."));

        // when
        int exitCode = commandLine.execute("job.failed");

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors())
                .isEqualToIgnoringNewLines("x Sending job.failed failed: Too many test events. Try again in a minute.");
    }

    private static WhaleDocConfig createConfig(String accessToken) {

        return WhaleDocConfig.builder()
                .cliId(CLI_ID)
                .accessToken(accessToken)
                .build();
    }
}
