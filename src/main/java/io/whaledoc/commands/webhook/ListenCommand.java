package io.whaledoc.commands.webhook;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.exceptions.ForwardException;
import io.whaledoc.http.SseConnection;
import io.whaledoc.http.SseEvent;
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
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

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
                            whaledoc listen --events document.created,document.completed \\
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

    @Spec
    private CommandSpec spec;

    private URI forwardTarget;

    public ListenCommand(ConfigManager configManager, WebhookClient webhookClient, Console console) {
        this.configManager = configManager;
        this.webhookClient = webhookClient;
        this.console = console;
        this.eventPrinter = new EventPrinter(console, Clock.systemDefaultZone());
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

        SseConnection connection = webhookClient.listen(config.accessToken(), eventFilter, this::handleEvent);
        Runtime.getRuntime().addShutdownHook(new Thread(connection::close));

        try {
            if (awaitConnected(connection)) {
                printReady();
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
     * Waits until the event stream is open, so "Ready!" is only shown when events can actually arrive.
     *
     * @return false when the connection was closed before it opened, e.g. with Ctrl+C
     */
    private boolean awaitConnected(SseConnection connection) throws InterruptedException {

        Spinner spinner = console.spinner("Connecting to WhaleDoc...");
        spinner.start();

        try {
            connection.connected().get();
            return true;

        } catch (CancellationException e) {
            return false;

        } catch (ExecutionException e) {
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

        return "Stopped listening: " + e.getMessage();
    }

    private Set<String> resolveEvents() {

        Set<String> requestedEvents = Set.copyOf(Arrays.asList(events));

        if (requestedEvents.contains(WebhookEvents.ALL)) {
            return Set.of(WebhookEvents.ALL);
        }

        Set<String> invalidEvents = requestedEvents.stream()
                .filter(event -> !WebhookEvents.ALLOWED.contains(event))
                .collect(Collectors.toSet());

        if (!invalidEvents.isEmpty()) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Unknown event(s): " + String.join(", ", invalidEvents)
            );
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

    private void handleEvent(SseEvent event) {

        if (forwardTarget == null) {
            eventPrinter.printEvent(event);
            return;
        }

        // A failing local endpoint is reported per event; listening continues for the next one
        try {
            eventPrinter.printForwarded(event, webhookClient.forward(forwardTarget, event));

        } catch (ForwardException e) {
            log.warn("Forwarding {} failed", event.event(), e);
            eventPrinter.printForwardFailed(event, e.getMessage());
        }
    }
}
