package io.whaledoc.commands.auth;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.auth.CurrentLogin;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine.Command;

import java.util.Optional;
import java.util.concurrent.Callable;

@Slf4j
@Command(
        name = "status",
        mixinStandardHelpOptions = true,
        description = "Show the account you're logged in as and the workspace the CLI works in."
)
public class StatusCommand implements Callable<Integer> {

    private static final String LABEL_FORMAT = "%-11s";

    private final ConfigManager configManager;
    private final AuthClient authClient;
    private final Console console;

    public StatusCommand(ConfigManager configManager, AuthClient authClient, Console console) {
        this.configManager = configManager;
        this.authClient = authClient;
        this.console = console;
    }

    @Override
    public Integer call() {

        Optional<String> token = Optional.ofNullable(configManager.load().accessToken()).filter(StringUtils::isNotBlank);

        if (token.isEmpty()) {

            console.error("You're not logged in. Run " + console.bold("whaledoc login") + " to log in.");
            return 1;
        }

        Spinner spinner = console.spinner("Checking your login...");
        spinner.start();

        try {
            Optional<CurrentLogin> login = authClient.currentLogin(token.get());
            spinner.stop();
            return login.map(this::print).orElseGet(this::noLongerValid);

        } catch (ApiException e) {
            spinner.stop();
            log.error("Checking the login failed", e);
            console.error(e.getMessage());
            return 1;
        }
    }

    private Integer print(CurrentLogin login) {

        console.println("Logged in as " + console.bold(login.account().email()) + " " + console.dim("(" + login.account().fullName() + ")"));
        console.println(label("Workspace") + login.currentWorkspace()
                .map(workspace -> console.bold(workspace.name()) + "  " + console.dim(workspace.slug()))
                .orElse("none. Run " + console.bold("whaledoc workspace switch") + " to pick one."));
        console.println(label("Device") + login.deviceName());
        return 0;
    }

    private Integer noLongerValid() {

        console.error("Your login is no longer valid. Run " + console.bold("whaledoc login") + " to log in again.");
        return 1;
    }

    private static String label(String name) {

        return LABEL_FORMAT.formatted(name);
    }
}
