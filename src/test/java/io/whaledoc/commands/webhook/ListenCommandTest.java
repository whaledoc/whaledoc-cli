package io.whaledoc.commands.webhook;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.http.SseConnection;
import io.whaledoc.http.SseEvent;
import io.whaledoc.webhook.WebhookClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ListenCommandTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String FORWARD_URL = "http://localhost:8080/events";

    @Mock
    private ConfigManager configManager;

    @Mock
    private WebhookClient webhookClient;

    @Mock
    private SseConnection connection;

    @Captor
    private ArgumentCaptor<Consumer<SseEvent>> eventConsumer;

    private CommandLine commandLine;

    @BeforeEach
    void setUp() {
        commandLine = new CommandLine(new ListenCommand(configManager, webhookClient));
    }

    @Test
    void shouldNotListenWhenUserIsNotLoggedIn() {

        // given
        given(configManager.load()).willReturn(createConfig(null));

        // when
        int exitCode = commandLine.execute();

        // then
        assertThat(exitCode).isZero();
        then(webhookClient).shouldHaveNoInteractions();
    }

    @Test
    void shouldListenForAllEventsWhenNoEventsAreGiven() {

        // given
        givenUserIsLoggedInAndListening();

        // when
        int exitCode = commandLine.execute();

        // then
        assertThat(exitCode).isZero();
        then(webhookClient).should().listen(eq(ACCESS_TOKEN), eq(Set.of("*")), any());
    }

    @Test
    void shouldListenForRequestedEventsWhenEventsAreGiven() {

        // given
        givenUserIsLoggedInAndListening();

        // when
        int exitCode = commandLine.execute("--events", "document.created,document.failed");

        // then
        assertThat(exitCode).isZero();
        then(webhookClient).should().listen(eq(ACCESS_TOKEN), eq(Set.of("document.created", "document.failed")), any());
    }

    @Test
    void shouldRejectCommandWhenEventIsUnknown() {

        // given
        given(configManager.load()).willReturn(createConfig(ACCESS_TOKEN));

        // when
        int exitCode = commandLine.execute("--events", "document.deleted");

        // then
        assertThat(exitCode).isEqualTo(2);
        then(webhookClient).shouldHaveNoInteractions();
    }

    @Test
    void shouldForwardEventWhenForwardUrlIsGiven() {

        // given
        givenUserIsLoggedInAndListening();
        SseEvent event = new SseEvent("document.created", "{\"id\":\"doc-1\"}", "1");
        commandLine.execute("--forward-to", FORWARD_URL);
        then(webhookClient).should().listen(eq(ACCESS_TOKEN), anySet(), eventConsumer.capture());

        // when
        eventConsumer.getValue().accept(event);

        // then
        then(webhookClient).should().forward(FORWARD_URL, event);
    }

    private void givenUserIsLoggedInAndListening() {

        given(configManager.load()).willReturn(createConfig(ACCESS_TOKEN));
        given(webhookClient.listen(eq(ACCESS_TOKEN), anySet(), any())).willReturn(connection);
    }

    private WhaleDocConfig createConfig(String accessToken) {

        return WhaleDocConfig.builder()
                .cliId("3f1b2c4d-5e6f-4a1b-9c2d-7e8f9a0b1c2d")
                .accessToken(accessToken)
                .build();
    }
}
