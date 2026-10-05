package io.whaledoc.commands.auth;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.auth.AuthSession;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.utility.Spinner;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Command(
        name = "login",
        description = "Log in to your WhaleDoc account"
)
public class LoginCommand implements Callable<Integer> {

    private final ConfigManager configManager = new ConfigManager();

    private final AuthClient authClient = new AuthClient();

    @Override
    public Integer call() {

        WhaleDocConfig config = configManager.load();

        log.debug(
                "Starting authentication for CLI {}",
                config.cliId()
        );

        Spinner spinner = new Spinner("Awaiting authentication...");
        try {
           AuthSession session = authClient.createSession(config.cliId());

            System.out.println("Your authentication code is: %s".formatted(session.authCode()));
            System.out.println("This authentication code verifies your authentication with WhaleDoc.");
            System.out.println("Press Enter to open the browser or visit: %s".formatted(session.authorizationUrl()));
            System.out.println("\n(^C to quit)");

            CompletableFuture<String> authentication = CompletableFuture.supplyAsync(
                    () -> authClient.awaitAuthentication(session)
            );

            new BufferedReader(new InputStreamReader(System.in)).readLine();

            if (!authentication.isDone()) {
                Desktop.getDesktop().browse(session.authorizationUrl());
            }

            Desktop.getDesktop().browse(session.authorizationUrl());

            spinner.start();

            String accessToken = authClient.awaitAuthentication(session);

            configManager.saveToFile(
                    WhaleDocConfig.builder()
                            .cliId(config.cliId())
                            .accessToken(accessToken)
                            .build()

            );
            spinner.stop();

            System.out.println("> Authenticated");

            return 0;

        } catch (Exception e) {

            spinner.stop();
            log.error("! Authentication failed", e);
            System.out.println("! Authentication failed");

            return 1;
        }
    }

    private void openAuthorizationPage(URI authorizationUrl) {

        if (!Desktop.isDesktopSupported()) {
            throw new ApiException(
                    "Unable to open the authorization page automatically. " +
                    "Please open this URL manually: " + authorizationUrl
            );
        }

        try {
            Desktop.getDesktop().browse(authorizationUrl);
        } catch (IOException e) {
            throw new ApiException("Unable to open the authorization page. Please open this URL manually: " + authorizationUrl, e);
        }
    }
}