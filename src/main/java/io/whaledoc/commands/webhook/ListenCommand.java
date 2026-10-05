package io.whaledoc.commands.webhook;

import io.whaledoc.http.SseConnection;
import io.whaledoc.http.SseEvent;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.webhook.WebhookClient;
import io.whaledoc.webhook.WebhookEvents;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
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
public class ListenCommand implements Runnable {

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
            description = "The URL to forward webhook events to."
    )
    private String forwardTo;

    public ListenCommand(ConfigManager configManager, WebhookClient webhookClient) {
        this.configManager = configManager;
        this.webhookClient = webhookClient;
    }

    @Override
    public void run() {
        WhaleDocConfig config = configManager.load();

        if (config.accessToken() == null || config.accessToken().isBlank()) {
            System.out.println("You are not logged in. Run 'whaledoc login' first.");
            return;
        }

        Set<String> eventFilter = resolveEvents();

        SseConnection connection = webhookClient.listen(
                config.accessToken(),
                eventFilter,
                this::handleEvent
        );

        Runtime.getRuntime().addShutdownHook(new Thread(connection::close));

        if (forwardTo != null && !forwardTo.isBlank()) {
            System.out.println("Forwarding events to " + forwardTo);
        }

        System.out.println();
        System.out.println("Listening for webhook events. (^C to quit)");

        try {
            connection.awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connection.close();
        }
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
                    new CommandLine(this),
                    "Unknown event(s): " + String.join(", ", invalidEvents)
            );
        }

        return requestedEvents;
    }

    private void handleEvent(SseEvent event) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        if (forwardTo != null && !forwardTo.isBlank()) {
            webhookClient.forward(forwardTo, event);
            System.out.println(timestamp + "  --> " + event.event());
            return;
        }

        System.out.println();
        System.out.println(timestamp + "  --> " + event.event());
        System.out.println(event.data());

    }
}