package io.whaledoc.commands.webhook;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.TestConsole;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.http.Reconnect;
import io.whaledoc.http.SseConnection;
import io.whaledoc.http.SseEvent;
import io.whaledoc.http.SseListener;
import io.whaledoc.webhook.ForwardResult;
import io.whaledoc.webhook.SignedEvent;
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
import java.util.Map;
import java.util.Optional;
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
import static org.mockito.Mockito.timeout;

@ExtendWith(MockitoExtension.class)
class ListenCommandTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String FORWARD_URL = "http://localhost:8080/events";
    private static final String SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
    private static final SseEvent MESSAGE = new SseEvent("job.completed", "{\"headers\":{},\"body\":\"{}\"}", "evt_1");
    private static final SignedEvent EVENT = new SignedEvent("evt_1", "job.completed",
            Map.of("webhook-id", "evt_1"), "{\"id\":\"evt_1\"}");
    private static final SseEvent READY = new SseEvent("ready", "{\"signingSecret\":\"" + SECRET + "\"}", null);
    private static final SseEvent DISCONNECTED = new SseEvent("disconnected", "{\"by\":\"Jane Doe\"}", null);

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
    void shouldListenForAllEventsWithoutForwardUrlWhenNoneAreGiven() {

        // given
        givenUserIsLoggedInAndListening();

        // when
        int exitCode = commandLine.execute();

        // then
        assertThat(exitCode).isZero();
        then(webhookClient).should().listen(eq(ACCESS_TOKEN), eq(Optional.empty()), eq(Set.of("*")), any());
    }

    @Test
    void shouldListenForRequestedEventsWhenEventsAreGiven() {

        // given
        givenUserIsLoggedInAndListening();

        // when
        int exitCode = commandLine.execute("--events", "job.completed,document.created");

        // then
        assertThat(exitCode).isZero();
        then(webhookClient).should()
                .listen(eq(ACCESS_TOKEN), any(), eq(Set.of("job.completed", "document.created")), any());
    }

    @Test
    void shouldSendParsedForwardUrlWhenForwardUrlHasNoScheme() {

        // given
        givenUserIsLoggedInAndListening();

        // when
        commandLine.execute("--forward-to", "localhost:8080/events");

        // then
        then(webhookClient).should().listen(eq(ACCESS_TOKEN), eq(Optional.of(URI.create(FORWARD_URL))), anySet(), any());
    }

    @Test
    void shouldShowSigningSecretWhenStreamIsReady() {

        // given
        SseListener listener = givenListeningWithForwardUrl(FORWARD_URL);
        given(webhookClient.field(READY, "signingSecret")).willReturn(Optional.of(SECRET));

        // when
        listener.onEvent(READY);

        // then
        assertThat(testConsole.output()).contains("Your webhook signing secret is " + SECRET);
    }

    @Test
    void shouldWarnWhenSigningSecretChangedAfterReconnect() {

        // given
        SseListener listener = givenListeningWithForwardUrl(FORWARD_URL);
        SseEvent rotated = new SseEvent("ready", "{\"signingSecret\":\"whsec_new\"}", null);
        given(webhookClient.field(READY, "signingSecret")).willReturn(Optional.of(SECRET));
        given(webhookClient.field(rotated, "signingSecret")).willReturn(Optional.of("whsec_new"));

        // when
        listener.onEvent(READY);
        listener.onEvent(READY);
        listener.onEvent(rotated);

        // then
        assertThat(testConsole.output()).containsOnlyOnce("Your webhook signing secret is");
        assertThat(testConsole.errors()).contains("The webhook signing secret changed. Your local app needs the new one: whsec_new");
    }

    @Test
    void shouldStopAndNameMemberWhenDisconnectedFromTheApp() {

        // given
        SseListener listener = givenListeningWithForwardUrl(FORWARD_URL);
        given(webhookClient.field(DISCONNECTED, "by")).willReturn(Optional.of("Jane Doe"));

        // when
        listener.onEvent(DISCONNECTED);

        // then
        assertThat(testConsole.errors())
                .contains("Jane Doe disconnected this listener from the WhaleDoc app. Run whaledoc listen to start again.");
        then(connection).should().close();
    }

    @Test
    void shouldForwardSignedEventWhenForwardUrlIsGiven() {

        // given
        SseListener listener = givenListeningWithForwardUrl("localhost:8080/events");
        given(webhookClient.parse(MESSAGE)).willReturn(EVENT);
        given(webhookClient.forward(URI.create(FORWARD_URL), EVENT)).willReturn(ForwardResult.answered(200, "ok", 12));

        // when
        listener.onEvent(MESSAGE);

        // then
        assertThat(testConsole.output()).containsPattern("-> job\\.completed +\\[200]");
    }

    @Test
    void shouldReportLocalAnswerWhenEventIsForwarded() {

        // given
        SseListener listener = givenListeningWithForwardUrl(FORWARD_URL);
        ForwardResult result = ForwardResult.answered(200, "ok", 12);
        given(webhookClient.parse(MESSAGE)).willReturn(EVENT);
        given(webhookClient.forward(URI.create(FORWARD_URL), EVENT)).willReturn(result);

        // when
        listener.onEvent(MESSAGE);

        // then
        then(webhookClient).should(timeout(1_000)).reportAnswer(ACCESS_TOKEN, URI.create(FORWARD_URL), EVENT, result);
    }

    @Test
    void shouldPrintFailureAndKeepListeningWhenLocalEndpointIsUnreachable() {

        // given
        SseListener listener = givenListeningWithForwardUrl(FORWARD_URL);
        given(webhookClient.parse(MESSAGE)).willReturn(EVENT);
        given(webhookClient.forward(any(), any())).willReturn(ForwardResult.failed(ForwardResult.Kind.CONNECTION_FAILED,
                "Unable to connect to localhost:8080. Is the server running?", 3));

        // when
        Throwable thrown = catchThrowable(() -> listener.onEvent(MESSAGE));

        // then
        assertThat(thrown).isNull();
        assertThat(testConsole.output()).contains("failed Unable to connect to localhost:8080. Is the server running?");
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
    void shouldTellUserWhenConnectionDropsAndIsRestored() {

        // given
        SseListener listener = givenListeningWithForwardUrl(FORWARD_URL);

        // when
        listener.onReconnecting(new Reconnect(3, 10, Duration.ofSeconds(4), null));
        listener.onReconnected();

        // then
        assertThat(testConsole.errors().lines().toList())
                .containsExactly("Connecting to WhaleDoc...", "! Connection lost. Reconnecting in 4s (attempt 3 of 10)...");
        assertThat(testConsole.output()).endsWith("+ Reconnected" + System.lineSeparator());
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
        then(webhookClient).should().listen(eq(ACCESS_TOKEN), any(), anySet(), listenerCaptor.capture());

        return listenerCaptor.getValue();
    }

    private void givenUserIsLoggedInAndListening() {

        givenUserIsLoggedInAndConnecting();
        given(connection.connected()).willReturn(CompletableFuture.completedFuture(null));
    }

    private void givenUserIsLoggedInAndConnecting() {

        given(configManager.load()).willReturn(createConfig(ACCESS_TOKEN));
        given(webhookClient.listen(eq(ACCESS_TOKEN), any(), anySet(), any())).willReturn(connection);
    }

    private WhaleDocConfig createConfig(String accessToken) {

        return WhaleDocConfig.builder()
                .cliId("3f1b2c4d-5e6f-4a1b-9c2d-7e8f9a0b1c2d")
                .accessToken(accessToken)
                .build();
    }
}
