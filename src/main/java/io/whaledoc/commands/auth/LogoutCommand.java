package io.whaledoc.commands.auth;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.utility.Spinner;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Slf4j
@Command(
        name = "logout",
        description = "Logout of your WhaleDoc account"
)
public class LogoutCommand implements Callable<Integer> {

    private final ConfigManager configManager;
    private final AuthClient authClient;

    public LogoutCommand() {
        this(new ConfigManager(), new AuthClient());
    }

    public LogoutCommand(ConfigManager configManager, AuthClient authClient) {
        this.configManager = configManager;
        this.authClient = authClient;
    }

    @Override
    public Integer call() {
        WhaleDocConfig config = configManager.load();

        if (StringUtils.isBlank(config.accessToken())) {
            System.out.println("You are already logged out.");
            return 0;
        }

        Spinner spinner = new Spinner("Logging out...");
        spinner.start();

        try {
            authClient.logout(config.accessToken());

            configManager.saveToFile(
                    WhaleDocConfig.builder()
                            .cliId(config.cliId())
                            .build()
            );

            spinner.stop();
            System.out.println("> Logged out");

            return 0;

        } catch (ApiException e) {

            spinner.stop();
            System.out.println("! Logout failed");

            log.error("Logout failed", e);

            return 1;
        }
    }
}