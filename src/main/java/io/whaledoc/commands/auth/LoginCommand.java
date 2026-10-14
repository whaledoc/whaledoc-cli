package io.whaledoc.commands.auth;

import io.whaledoc.auth.AuthClient;
import io.whaledoc.auth.AuthSession;
import io.whaledoc.auth.AuthenticationEvent;
import io.whaledoc.auth.CurrentLogin;
import io.whaledoc.config.ConfigManager;
import io.whaledoc.config.WhaleDocConfig;
import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.ApiException;
import io.whaledoc.utility.BrowserLauncher;
import io.whaledoc.utility.ComputerName;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.net.URI;
import java.util.Optional;
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

    @Option(
            names = {"--device-name"},
            paramLabel = "string",
            description = "The name your WhaleDoc sessions show for this login. Defaults to this computer's name."
    )
    private String deviceName;

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
        Optional<String> savedToken = Optional.ofNullable(config.accessToken()).filter(StringUtils::isNotBlank);

        try {
            Optional<CurrentLogin> currentLogin = savedToken.flatMap(authClient::currentLogin);

            if (currentLogin.isPresent()) {

                refuse(currentLogin.get());
                return 1;
            }

            if (savedToken.isPresent()) {
                console.println("Your previous login no longer works. Starting a new one.");
            }

            AuthenticationEvent approved = logIn(config);
            console.success(loggedIn(approved));
            return 0;

        } catch (ApiException e) {
            log.error("Login failed", e);
            console.error("Login failed: " + e.getMessage());
            return 1;
        }
    }

    // One login per installation: a second one would leave the first signed in, unused, until it expires
    private void refuse(CurrentLogin login) {

        String workspace = login.currentWorkspace()
                .map(current -> ", working in " + console.bold(current.name()))
                .orElse("");

        console.error("You're already logged in as " + console.bold(login.account().email()) + workspace + ".");
        console.println("Run " + console.bold("whaledoc logout") + " first to log in with another account.");
    }

    private AuthenticationEvent logIn(WhaleDocConfig config) {

        AuthSession session = authClient.createSession(config.cliId(),
                Optional.ofNullable(deviceName).or(ComputerName::current));
        CompletableFuture<AuthenticationEvent> authentication = authClient.waitForAuthentication(session);
        Spinner spinner = console.spinner("Waiting for you to approve the login... " + console.dim("(^C to quit)"));

        printInstructions(session);
        openBrowserOnEnter(session.authorizationUrl(), authentication, spinner);

        AuthenticationEvent approved = awaitAuthentication(authentication, spinner);
        configManager.saveToFile(
                WhaleDocConfig.builder()
                        .cliId(config.cliId())
                        .accessToken(approved.accessToken())
                        .build()
        );

        return approved;
    }

    // "Logged in as mara@acme.com. Working in Acme HR.": the login is the account's, the workspace only where it starts
    private String loggedIn(AuthenticationEvent approved) {

        String account = approved.account()
                .map(email -> "Logged in as " + console.bold(email) + ".")
                .orElse("Logged in.");
        String workspace = approved.workspace()
                .map(name -> " Working in " + console.bold(name) + ".")
                .orElse("");

        return account + workspace;
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
    private void openBrowserOnEnter(URI authorizationUrl, CompletableFuture<AuthenticationEvent> authentication, Spinner spinner) {

        Thread.ofVirtual().start(() -> {

            if (!console.waitForEnter() || authentication.isDone()) {
                return;
            }

            spinner.returnAfterEnter();

            if (!browserLauncher.open(authorizationUrl)) {
                console.warning("Unable to open a browser. Please visit the URL above.");
            }
        });
    }

    private AuthenticationEvent awaitAuthentication(CompletableFuture<AuthenticationEvent> authentication, Spinner spinner) {

        spinner.start();

        try {
            return authentication.get();

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
