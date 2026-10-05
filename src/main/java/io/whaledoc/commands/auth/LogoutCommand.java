package io.whaledoc.commands.auth;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Slf4j
@Command(
        name = "logout",
        mixinStandardHelpOptions = true,
        description = "Log out of your WhaleDoc account."
)
public class LogoutCommand implements Callable<Integer> {

    private final ConfigManager configManager;
    private final AuthClient authClient;
    private final Console console;

    public LogoutCommand(ConfigManager configManager, AuthClient authClient, Console console) {
        this.configManager = configManager;
        this.authClient = authClient;
        this.console = console;
    }

    @Override
    public Integer call() {

        WhaleDocConfig config = configManager.load();

        if (StringUtils.isBlank(config.accessToken())) {
            console.println("You're already logged out.");
            return 0;
        }

        Spinner spinner = console.spinner("Logging out...");
        spinner.start();

        try {
            authClient.logout(config.accessToken());

            configManager.saveToFile(
                    WhaleDocConfig.builder()
                            .cliId(config.cliId())
                            .build()
            );

            spinner.stop();
            console.success("Logged out");
            return 0;

        } catch (ApiException e) {
            spinner.stop();
            log.error("Logout failed", e);
            console.error("Logout failed: " + e.getMessage());
            return 1;
        }
    }
}
