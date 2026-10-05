package io.whaledoc.commands.auth;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.auth.AuthSession;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.utility.BrowserLauncher;
import io.whaledoc.utility.Spinner;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Command(
        name = "login",
        mixinStandardHelpOptions = true,
        description = "Log in to your WhaleDoc account."
)
public class LoginCommand implements Callable<Integer> {

    private final ConfigManager configManager;
    private final AuthClient authClient;
    private final BrowserLauncher browserLauncher;
    private final InputStream input;

    public LoginCommand(ConfigManager configManager, AuthClient authClient, BrowserLauncher browserLauncher, InputStream input) {
        this.configManager = configManager;
        this.authClient = authClient;
        this.browserLauncher = browserLauncher;
        this.input = input;
    }

    @Override
    public Integer call() {

        WhaleDocConfig config = configManager.load();

        try {
            AuthSession session = authClient.createSession(config.cliId());
            CompletableFuture<String> accessToken = authClient.waitForAuthentication(session);

            printInstructions(session);
            openBrowserOnEnter(session.authorizationUrl(), accessToken);

            configManager.saveToFile(
                    WhaleDocConfig.builder()
                            .cliId(config.cliId())
                            .accessToken(awaitAccessToken(accessToken))
                            .build()
            );

            System.out.println("> Authenticated");
            return 0;

        } catch (ApiException e) {
            log.error("Login failed", e);
            System.out.println("! Login failed: " + e.getMessage());
            return 1;
        }
    }

    private static void printInstructions(AuthSession session) {

        System.out.println("Your authentication code is: %s".formatted(session.authCode()));
        System.out.println("This authentication code verifies your authentication with WhaleDoc.");
        System.out.println("Press Enter to open the browser or visit: %s".formatted(session.authorizationUrl()));
        System.out.println("\n(^C to quit)");
    }

    // Waits for Enter in the background, so a login approved through the printed URL finishes without it
    private void openBrowserOnEnter(URI authorizationUrl, CompletableFuture<String> accessToken) {

        Thread.ofVirtual().start(() -> {

            if (waitForEnter() && !accessToken.isDone() && !browserLauncher.open(authorizationUrl)) {
                System.out.println("! Unable to open a browser. Please visit: " + authorizationUrl);
            }
        });
    }

    private boolean waitForEnter() {

        try {
            return input.read() != -1;
        } catch (IOException e) {
            return false;
        }
    }

    private static String awaitAccessToken(CompletableFuture<String> accessToken) {

        Spinner spinner = new Spinner("Awaiting authentication...");
        spinner.start();

        try {
            return accessToken.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("The login was interrupted.", e);

        } catch (ExecutionException e) {
            throw toApiException(e.getCause());

        } finally {
            spinner.stop();
        }
    }

    private static ApiException toApiException(Throwable cause) {

        if (cause instanceof ApiException apiException) {
            return apiException;
        }

        if (cause instanceof TimeoutException) {
            return new ApiException("The login was not approved within 5 minutes.");
        }

        return new ApiException("Authentication failed.", cause);
    }
}
