package io.whaledoc.commands.webhook;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.TestConsole;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.exceptions.ForwardException;
import io.whaledoc.http.SseConnection;
import io.whaledoc.http.Reconnect;
import io.whaledoc.http.SseEvent;
import io.whaledoc.http.SseListener;
import io.whaledoc.webhook.WebhookClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class ListenCommandTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String FORWARD_URL = "http://localhost:8080/events";
    private static final SseEvent EVENT = new SseEvent("document.created", "{\"id\":\"doc-1\"}", "1");

    @Mock
    private ConfigManager configManager;

    @Mock
    private WebhookClient webhookClient;

    @Mock
    private SseConnection connection;

    @Captor
    private ArgumentCaptor<SseListener> listenerCaptor;

    private final TestConsole testConsole = TestConsole.plain();

    private CommandLine commandLine;

    @BeforeEach
    void setUp() {
        commandLine = new CommandLine(new ListenCommand(configManager, webhookClient, testConsole.console()));
    }

    @Test
    void shouldReturnErrorWithoutListeningWhenUserIsNotLoggedIn() {

        // given
        given(configManager.load()).willReturn(createConfig(null));

        // when
        int exitCode = commandLine.execute();

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.errors()).isEqualToIgnoringNewLines("x You're not logged in. Run whaledoc login first.");
        then(webhookClient).shouldHaveNoInteractions();
    }

    @Test
    void shouldReturnErrorWhenConnectionIsRejected() throws InterruptedException {

        // given
        givenUserIsLoggedInAndListening();
        willThrow(new ApiException(401, "Unauthorized")).given(connection).awaitCompletion();

        // when
        int exitCode = commandLine.execute();

        // then
        assertThat(exitCode).isEqualTo(1);
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
        SseListener listener = givenListeningWithForwardUrl("localhost:8080/events");

        // when
        listener.onEvent(EVENT);

        // then
        then(webhookClient).should().forward(URI.create(FORWARD_URL), EVENT);
    }

    @Test
    void shouldReturnErrorWithoutPrintingReadyWhenConnectionCannotBeOpened() {

        // given
        givenUserIsLoggedInAndConnecting();
        given(connection.connected()).willReturn(CompletableFuture.failedFuture(new ApiException("Unable to connect to WhaleDoc after 10 attempts.")));

        // when
        int exitCode = commandLine.execute();

        // then
        assertThat(exitCode).isEqualTo(1);
        assertThat(testConsole.output()).doesNotContain("Ready!");
        assertThat(testConsole.errors()).contains("Unable to connect to WhaleDoc after 10 attempts.");
    }

    @Test
    void shouldPrintResponseStatusWhenEventIsForwarded() {

        // given
        SseListener listener = givenListeningWithForwardUrl(FORWARD_URL);
        given(webhookClient.forward(URI.create(FORWARD_URL), EVENT)).willReturn(200);

        // when
        listener.onEvent(EVENT);

        // then
        assertThat(testConsole.output()).containsPattern("-> document\\.created +\\[200]");
    }

    @Test
    void shouldTellUserWhenConnectionDropsAndIsRestored() {

        // given
        SseListener listener = givenListeningWithForwardUrl(FORWARD_URL);

        // when
        listener.onReconnecting(new Reconnect(3, 10, Duration.ofSeconds(4), null));
        listener.onReconnected();

        // then
        assertThat(testConsole.errors()).isEqualToIgnoringNewLines("! Connection lost. Reconnecting in 4s (attempt 3 of 10)...");
        assertThat(testConsole.output()).endsWith("+ Reconnected" + System.lineSeparator());
    }

    @Test
    void shouldKeepListeningWhenForwardingEventFails() {

        // given
        SseListener listener = givenListeningWithForwardUrl(FORWARD_URL);
        given(webhookClient.forward(any(), any())).willThrow(new ForwardException("Unable to reach " + FORWARD_URL));

        // when
        Throwable thrown = catchThrowable(() -> listener.onEvent(EVENT));

        // then
        assertThat(thrown).isNull();
    }

    @Test
    void shouldRejectCommandWhenForwardUrlIsInvalid() {

        // given
        given(configManager.load()).willReturn(createConfig(ACCESS_TOKEN));

        // when
        int exitCode = commandLine.execute("--forward-to", "ftp://localhost/events");

        // then
        assertThat(exitCode).isEqualTo(2);
        then(webhookClient).shouldHaveNoInteractions();
    }

    // Runs the command and returns the handler it registered for incoming events
    private SseListener givenListeningWithForwardUrl(String forwardUrl) {

        givenUserIsLoggedInAndListening();
        commandLine.execute("--forward-to", forwardUrl);
        then(webhookClient).should().listen(eq(ACCESS_TOKEN), anySet(), listenerCaptor.capture());

        return listenerCaptor.getValue();
    }

    private void givenUserIsLoggedInAndListening() {

        givenUserIsLoggedInAndConnecting();
        given(connection.connected()).willReturn(CompletableFuture.completedFuture(null));
    }

    private void givenUserIsLoggedInAndConnecting() {

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
