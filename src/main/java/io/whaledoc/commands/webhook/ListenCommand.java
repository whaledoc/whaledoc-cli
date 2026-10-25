package io.whaledoc.commands.webhook;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.http.Reconnect;
import io.whaledoc.http.WebSocketConnection;
import io.whaledoc.webhook.ForwardResult;
import io.whaledoc.webhook.ListenListener;
import io.whaledoc.webhook.ListenMessage;
import io.whaledoc.webhook.SignedEvent;
import io.whaledoc.webhook.WebhookClient;
import io.whaledoc.webhook.WebhookEvents;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Command(
        name = "listen",
        mixinStandardHelpOptions = true,
        description = "Listen for webhook events.",
        header = {
                "Listen for webhook events.",
                "%nThe listen command watches and forwards webhook events from WhaleDoc to your",
                "local machine by connecting directly to the WhaleDoc API. You can filter events",
                "or forward them to a local HTTP endpoint.",
                """
                        
                         @|bold Examples:|@
                            whaledoc listen
                            whaledoc listen --events job.completed,job.failed \\
                            --forward-to localhost:8080/events
                        """
        },
        headerHeading = "",
        synopsisHeading = "%nUsage:%n  ",
        optionListHeading = "%nFlags:%n",
        customSynopsis = {
                "whaledoc listen [flags]"
        }
)
@Slf4j
public class ListenCommand implements Callable<Integer> {

    private static final String SIGNING_SECRET = "signingSecret";
    private static final String DISCONNECTED_BY = "by";
    private static final Duration READY_WAIT = Duration.ofSeconds(5);

    private final ConfigManager configManager;
    private final WebhookClient webhookClient;
    private final Console console;
    private final EventPrinter eventPrinter;

    @Option(
            names = {"-e", "--events"},
            paramLabel = "strings",
            split = ",",
            description = "A comma-separated list of specific events to listen for.",
            defaultValue = "*"
    )
    private String[] events;

    @Option(
            names = {"-f", "--forward-to"},
            paramLabel = "string",
            description = "The URL to forward webhook events to, e.g. localhost:8080/events (http:// is assumed)."
    )
    private String forwardTo;

    @Option(
            names = {"--print-json"},
            description = "Also print each event's JSON, under its line."
    )
    private boolean printJson;

    @Spec
    private CommandSpec spec;

    private URI forwardTarget;
    private boolean listening;
    private volatile WebSocketConnection connection;
    /** Read on the thread that handles WhaleDoc's messages only. */
    private String signingSecret;
    /** Opened once "Ready!" is printed, so the signing secret is printed below it. */
    private final CountDownLatch readyPrinted = new CountDownLatch(1);

    /** {@code appUrl}: the WhaleDoc app, where each printed event links to. */
    public ListenCommand(ConfigManager configManager, WebhookClient webhookClient, Console console, String appUrl) {
        this.configManager = configManager;
        this.webhookClient = webhookClient;
        this.console = console;
        this.eventPrinter = new EventPrinter(console, Clock.systemDefaultZone(), appUrl);
    }

    @Override
    public Integer call() {

        WhaleDocConfig config = configManager.load();

        if (config.accessToken() == null || config.accessToken().isBlank()) {
            console.error("You're not logged in. Run " + console.bold("whaledoc login") + " first.");
            return 1;
        }

        Set<String> eventFilter = resolveEvents();
        forwardTarget = resolveForwardTarget();
        String accessToken = config.accessToken();

        connection = forwardTarget == null
                ? webhookClient.listen(accessToken, eventFilter, new MessageHandler())
                : webhookClient.listen(accessToken, forwardTarget, eventFilter, new MessageHandler());
        // Ctrl+C and a closed terminal: a normal close rather than a dropped connection
        Runtime.getRuntime().addShutdownHook(new Thread(connection::close));

        try {
            if (awaitConnected(connection)) {
                listening = true;
                printReady();
                readyPrinted.countDown();
            }

            connection.awaitCompletion();
            return 0;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connection.close();
            return 0;

        } catch (ApiException e) {
            log.error("Stopped listening for webhook events", e);
            console.error(describeFailure(e));
            return 1;
        }
    }

    /**
     * Waits until the connection is open, so "Ready!" is only shown when events can actually arrive.
     *
     * @return false when the connection was closed before it opened, e.g. with Ctrl+C
     */
    private boolean awaitConnected(WebSocketConnection connection) throws InterruptedException {

        Spinner spinner = console.spinner("Connecting to WhaleDoc...");
        spinner.start();

        try {
            connection.connected().get();
            return true;

        } catch (CancellationException e) {
            return false;

        } catch (ExecutionException e) {

            if (e.getCause() instanceof CancellationException) {
                return false;
            }

            throw e.getCause() instanceof ApiException apiException
                    ? apiException
                    : new ApiException("Unable to connect to WhaleDoc.", e.getCause());

        } finally {
            spinner.stop();
        }
    }

    private void printReady() {

        console.println(console.bold(console.brand("Ready!")) + " Listening for webhook events " + console.dim("(^C to quit)"));

        if (forwardTarget != null) {
            console.println(console.dim("Forwarding to ") + console.cyan(forwardTarget.toString()));
        }

        console.println();
    }

    private String describeFailure(ApiException e) {

        if (e.statusCode() == 401 || e.statusCode() == 403) {
            return "Your login is no longer valid. Run " + console.bold("whaledoc login") + " and try again.";
        }

        // Before "Ready!" the reason alone says it all, e.g. "Unable to find api.whaledoc.io."
        return listening ? "Stopped listening: " + e.getMessage() : e.getMessage();
    }

    private final class MessageHandler implements ListenListener {

        @Override
        public void onMessage(ListenMessage message) {
            handleMessage(message);
        }

        @Override
        public void onReconnecting(Reconnect reconnect) {

            String when = reconnect.delay().isZero() ? "" : " in %ds".formatted(reconnect.delay().toSeconds());
            console.warning("Connection lost. Reconnecting%s (attempt %d of %d)...".formatted(
                    when, reconnect.attempt(), reconnect.maxAttempts()));
        }

        @Override
        public void onReconnected() {
            console.success("Reconnected");
        }
    }

    /** The names as given: events are matched by name, so a type the CLI has never seen can be filtered too. */
    private Set<String> resolveEvents() {

        Set<String> requestedEvents = Set.copyOf(Arrays.asList(events));

        if (requestedEvents.contains(WebhookEvents.ALL)) {
            return Set.of(WebhookEvents.ALL);
        }

        return requestedEvents;
    }

    private URI resolveForwardTarget() {

        if (forwardTo == null || forwardTo.isBlank()) {
            return null;
        }

        try {
            return WebhookClient.parseForwardUrl(forwardTo);

        } catch (IllegalArgumentException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), e.getMessage());
        }
    }

    /** A type this version doesn't know is skipped: WhaleDoc may add some. */
    private void handleMessage(ListenMessage message) {

        switch (message.type()) {
            case WebhookEvents.READY -> showSigningSecret(message);
            case WebhookEvents.EVENT -> handleWebhookEvent(message);
            case WebhookEvents.DISCONNECTED -> stopAsDisconnected(message);
            case null, default -> log.debug("Message of type {} skipped", message.type());
        }
    }

    /** Shown when the connection opens, and again after a reconnect only when a teammate rotated it in the app. */
    private void showSigningSecret(ListenMessage event) {

        Optional<String> secret;

        try {
            secret = webhookClient.field(event, SIGNING_SECRET);
        } catch (ApiException e) {
            console.warning("WhaleDoc sent no signing secret the CLI can read. Find it on the Webhooks page in the app.");
            return;
        }

        secret.filter(received -> !received.equals(signingSecret)).ifPresent(this::printSigningSecret);
    }

    private void printSigningSecret(String secret) {

        boolean first = Optional.ofNullable(signingSecret).isEmpty();
        signingSecret = secret;

        if (first) {
            awaitReadyPrinted();
            console.println("Your webhook signing secret is " + console.bold(secret) + " "
                    + console.dim("(your local app checks each event with it)"));
            console.println();
            return;
        }

        console.println("The webhook signing secret changed. Your local app needs the new one: " + console.bold(secret));
    }

    /** At most a moment: when Ctrl+C ended the wait for the connection, "Ready!" never comes. */
    private void awaitReadyPrinted() {

        try {
            readyPrinted.await(READY_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A teammate disconnected this listener from the WhaleDoc app: stop instead of reconnecting. */
    private void stopAsDisconnected(ListenMessage event) {

        String who = webhookClient.field(event, DISCONNECTED_BY)
                .map(name -> name + " disconnected this listener")
                .orElse("This listener was disconnected");
        console.warning(who + " from the WhaleDoc app. Run " + console.bold("whaledoc listen") + " to start again.");
        connection.close();
    }

    private void handleWebhookEvent(ListenMessage message) {

        SignedEvent event;

        try {
            event = webhookClient.parse(message);
        } catch (ApiException e) {
            log.warn("Unreadable webhook event {}", message.id(), e);
            console.warning(e.getMessage());
            return;
        }

        eventPrinter.printEvent(event);

        if (printJson) {
            printJson(event);
        }

        if (forwardTarget == null) {
            return;
        }

        // A failing local endpoint is reported per event; listening continues for the next one. Nothing is shown or
        // sent back for a forward cut short by quitting
        webhookClient.forward(forwardTarget, event).ifPresent(result -> {
            eventPrinter.printForwarded(forwardTarget, event, result);
            answer(event, result);
        });
    }

    /** Set apart by blank lines from the event's line and the next. */
    private void printJson(SignedEvent event) {

        console.println("");
        console.println(webhookClient.prettyJson(event.body()));
        console.println("");
    }

    /** On the same connection, and never shown: an answer that doesn't arrive doesn't stop listening. */
    private void answer(SignedEvent event, ForwardResult result) {

        try {
            connection.send(webhookClient.answerMessage(forwardTarget, event, result));
        } catch (RuntimeException e) {
            // Broad on purpose: the answer is only for the app's Events tab, and listening goes on
            log.debug("Sending the answer to {} failed", event.id(), e);
        }
    }
}
