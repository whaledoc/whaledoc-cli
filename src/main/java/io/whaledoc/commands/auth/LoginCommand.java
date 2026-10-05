package io.whaledoc.commands.auth;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.auth.AuthSession;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.utility.BrowserLauncher;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;

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
    private final Console console;

    public LoginCommand(ConfigManager configManager, AuthClient authClient, BrowserLauncher browserLauncher, Console console) {
        this.configManager = configManager;
        this.authClient = authClient;
        this.browserLauncher = browserLauncher;
        this.console = console;
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

            console.success("Logged in");
            return 0;

        } catch (ApiException e) {
            log.error("Login failed", e);
            console.error("Login failed: " + e.getMessage());
            return 1;
        }
    }

    // The code is shown in the browser too; comparing them protects against approving someone else's login
    private void printInstructions(AuthSession session) {

        console.println("Your authentication code is: " + console.bold(console.brand(session.authCode())));
        console.println(console.dim("Check that your browser shows the same code before you approve the login."));
        console.println();
        console.println("Press " + console.bold("Enter") + " to open the browser, or visit:");
        console.println(console.cyan(session.authorizationUrl().toString()));
        console.println();
    }

    // Waits for Enter in the background, so a login approved through the printed URL finishes without it
    private void openBrowserOnEnter(URI authorizationUrl, CompletableFuture<String> accessToken) {

        Thread.ofVirtual().start(() -> {

            if (console.waitForEnter() && !accessToken.isDone() && !browserLauncher.open(authorizationUrl)) {
                console.warning("Unable to open a browser. Please visit the URL above.");
            }
        });
    }

    private String awaitAccessToken(CompletableFuture<String> accessToken) {

        Spinner spinner = console.spinner("Waiting for you to approve the login... " + console.dim("(^C to quit)"));
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
