package io.whaledoc.commands.webhook;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.webhook.TriggeredEvent;
import io.whaledoc.webhook.WebhookClient;
import io.whaledoc.webhook.WebhookEvents;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

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
                            job.completed, job.failed, job.cancelled,
                            usage.limit_reached, usage.spending_cap_reached,
                            template.published, template.deleted

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

    private final ConfigManager configManager;
    private final WebhookClient webhookClient;
    private final Console console;

    @Parameters(
            index = "0",
            paramLabel = "<event>",
            description = "The event to send, e.g. job.completed."
    )
    private String event;

    @Spec
    private CommandSpec spec;

    public TriggerCommand(ConfigManager configManager, WebhookClient webhookClient, Console console) {
        this.configManager = configManager;
        this.webhookClient = webhookClient;
        this.console = console;
    }

    @Override
    public Integer call() {

        requireKnownEvent();
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

    private void requireKnownEvent() {

        if (!WebhookEvents.ALLOWED.contains(event)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "Unknown event: " + event + ". Choose one of: " + String.join(", ", WebhookEvents.ALLOWED));
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

        return "Sending " + event + " failed: " + e.getMessage();
    }
}
