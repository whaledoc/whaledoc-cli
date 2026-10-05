package io.whaledoc.commands.webhook;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.exceptions.ForwardException;
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

import java.net.URI;
import java.util.Set;
import java.util.function.Consumer;

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
    private ArgumentCaptor<Consumer<SseEvent>> eventConsumer;

    private CommandLine commandLine;

    @BeforeEach
    void setUp() {
        commandLine = new CommandLine(new ListenCommand(configManager, webhookClient));
    }

    @Test
    void shouldReturnErrorWithoutListeningWhenUserIsNotLoggedIn() {

        // given
        given(configManager.load()).willReturn(createConfig(null));

        // when
        int exitCode = commandLine.execute();

        // then
        assertThat(exitCode).isEqualTo(1);
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
        Consumer<SseEvent> handleEvent = givenListeningWithForwardUrl("localhost:8080/events");

        // when
        handleEvent.accept(EVENT);

        // then
        then(webhookClient).should().forward(URI.create(FORWARD_URL), EVENT);
    }

    @Test
    void shouldKeepListeningWhenForwardingEventFails() {

        // given
        Consumer<SseEvent> handleEvent = givenListeningWithForwardUrl(FORWARD_URL);
        given(webhookClient.forward(any(), any())).willThrow(new ForwardException("Unable to reach " + FORWARD_URL));

        // when
        Throwable thrown = catchThrowable(() -> handleEvent.accept(EVENT));

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
    private Consumer<SseEvent> givenListeningWithForwardUrl(String forwardUrl) {

        givenUserIsLoggedInAndListening();
        commandLine.execute("--forward-to", forwardUrl);
        then(webhookClient).should().listen(eq(ACCESS_TOKEN), anySet(), eventConsumer.capture());

        return eventConsumer.getValue();
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
