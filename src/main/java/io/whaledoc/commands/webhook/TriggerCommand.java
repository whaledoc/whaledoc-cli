package io.whaledoc.commands.webhook;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.webhook.TriggeredEvent;
import io.whaledoc.webhook.WebhookClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.time.Duration;
import java.util.concurrent.Callable;

@Command(
        name = "trigger",
        mixinStandardHelpOptions = true,
        description = "Send a test webhook event.",
        header = {
                "Send a test webhook event.",
                "%nThe trigger command sends a webhook event with sample data to your workspace,",
                "so you can try your integration without running a real job. The event reaches",
                "whaledoc listen like any other event, marked as a test.",
                """

                         @|bold Events:|@
                            Any webhook event, such as job.completed or template.published.
                            An unknown event is answered with the list of events you can send.

                         @|bold Examples:|@
                            whaledoc trigger job.completed
                            whaledoc trigger template.published
                        """
        },
        headerHeading = "",
        synopsisHeading = "%nUsage:%n  ",
        parameterListHeading = "%nArguments:%n",
        optionListHeading = "%nFlags:%n",
        customSynopsis = {
                "whaledoc trigger <event>"
        }
)
@Slf4j
public class TriggerCommand implements Callable<Integer> {

    private static final int BAD_REQUEST = 400;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final long SECONDS_PER_MINUTE = 60;

    private final ConfigManager configManager;
    private final WebhookClient webhookClient;
    private final Console console;

    @Parameters(
            index = "0",
            paramLabel = "<event>",
            description = "The event to send, e.g. job.completed."
    )
    private String event;

    public TriggerCommand(ConfigManager configManager, WebhookClient webhookClient, Console console) {
        this.configManager = configManager;
        this.webhookClient = webhookClient;
        this.console = console;
    }

    @Override
    public Integer call() {

        WhaleDocConfig config = configManager.load();

        if (StringUtils.isBlank(config.accessToken())) {
            console.error("You're not logged in. Run " + console.bold("whaledoc login") + " first.");
            return 1;
        }

        Spinner spinner = console.spinner("Sending " + event + "...");
        spinner.start();

        try {
            TriggeredEvent triggered = webhookClient.trigger(config.accessToken(), event);
            spinner.stop();
            printTriggered(triggered);
            return 0;

        } catch (ApiException e) {
            spinner.stop();
            log.error("Triggering {} failed", event, e);
            console.error(describeFailure(e));
            return 1;
        }
    }

    private void printTriggered(TriggeredEvent triggered) {

        console.success("Sent " + console.bold(triggered.type()) + " " + console.dim("(" + triggered.id() + ")"));
        console.println(console.dim("Run ") + console.bold("whaledoc listen") + console.dim(" to receive test events."));
    }

    private String describeFailure(ApiException e) {

        if (e.statusCode() == 401 || e.statusCode() == 403) {
            return "Your login is no longer valid. Run " + console.bold("whaledoc login") + " and try again.";
        }

        // An unknown event: the API's message names the events that can be sent
        if (e.statusCode() == BAD_REQUEST) {
            return e.getMessage();
        }

        if (e.statusCode() == TOO_MANY_REQUESTS) {
            return e.retryAfter()
                    .map(wait -> "You've sent a lot of test events. Try again in " + describeWait(wait) + ".")
                    .orElse(e.getMessage());
        }

        return "Sending " + event + " failed: " + e.getMessage();
    }

    // "1 second", "45 seconds", "2 minutes": rounded up, so waiting that long is always enough
    private static String describeWait(Duration wait) {

        long seconds = Math.max(1, wait.toSeconds() + (wait.toNanosPart() > 0 ? 1 : 0));

        if (seconds < SECONDS_PER_MINUTE) {
            return seconds == 1 ? "1 second" : seconds + " seconds";
        }

        long minutes = (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE;
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
