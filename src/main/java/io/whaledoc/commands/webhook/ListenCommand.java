package io.whaledoc.commands.webhook;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;
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

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConfigManager configManager;
    private final WebhookClient webhookClient;

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

    public ListenCommand(ConfigManager configManager, WebhookClient webhookClient) {
        this.configManager = configManager;
        this.webhookClient = webhookClient;
    }

    @Override
    public Integer call() {

        WhaleDocConfig config = configManager.load();

        if (config.accessToken() == null || config.accessToken().isBlank()) {
            System.out.println("You are not logged in. Run 'whaledoc login' first.");
            return 1;
        }

        Set<String> eventFilter = resolveEvents();
        forwardTarget = resolveForwardTarget();

        SseConnection connection = webhookClient.listen(
                config.accessToken(),
                eventFilter,
                this::handleEvent
        );

        Runtime.getRuntime().addShutdownHook(new Thread(connection::close));

        if (forwardTarget != null) {
            System.out.println("Forwarding events to " + forwardTarget);
        }

        System.out.println();
        System.out.println("Listening for webhook events. (^C to quit)");

        try {
            connection.awaitCompletion();
            return 0;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connection.close();
            return 0;

        } catch (ApiException e) {
            log.error("Stopped listening for webhook events", e);
            System.out.println("! " + describeFailure(e));
            return 1;
        }
    }

    private static String describeFailure(ApiException e) {

        if (e.statusCode() == 401 || e.statusCode() == 403) {
            return "Your login is no longer valid. Run 'whaledoc login' and try again.";
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

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        if (forwardTarget != null) {
            forwardEvent(timestamp, event);
            return;
        }

        System.out.println();
        System.out.println(timestamp + "  --> " + event.event());
        System.out.println(event.data());
    }

    // A failing local endpoint is reported per event; listening continues for the next one
    private void forwardEvent(String timestamp, SseEvent event) {

        try {
            int status = webhookClient.forward(forwardTarget, event);
            System.out.println("%s  --> %s [%d]".formatted(timestamp, event.event(), status));

        } catch (ForwardException e) {
            log.warn("Forwarding {} failed", event.event(), e);
            System.out.println("%s  --> %s ! %s".formatted(timestamp, event.event(), e.getMessage()));
        }
    }
}