package io.whaledoc.commands.update;

import io.whaledoc.console.Console;
import io.whaledoc.console.Spinner;
import io.whaledoc.exceptions.UpdateException;
import io.whaledoc.update.CurrentExecutable;
import io.whaledoc.update.UpdateService;
import io.whaledoc.update.Version;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Slf4j
@Command(
        name = "update",
        mixinStandardHelpOptions = true,
        description = "Update the WhaleDoc CLI to the latest version."
)
public class UpdateCommand implements Callable<Integer> {

    @Option(
            names = "--check",
            description = "Only check whether a newer version is available."
    )
    private boolean checkOnly;

    private final UpdateService updateService;
    private final Version currentVersion;
    private final Console console;

    public UpdateCommand(UpdateService updateService, Version currentVersion, Console console) {
        this.updateService = updateService;
        this.currentVersion = currentVersion;
        this.console = console;
    }

    @Override
    public Integer call() {

        if (!CurrentExecutable.isNativeImage()) {
            console.error("'whaledoc update' only works for an installed WhaleDoc CLI, not when running from a JAR.");
            return 1;
        }

        try {
            Version latestVersion = withSpinner("Checking for updates...", updateService::fetchLatestVersion);

            if (!latestVersion.isNewerThan(currentVersion)) {
                console.success("WhaleDoc CLI %s is up to date".formatted(currentVersion));
                return 0;
            }

            if (checkOnly) {
                console.println("A new version is available: %s %s %s".formatted(
                        currentVersion, console.arrow(), console.bold(console.brand(latestVersion.toString()))));
                console.println("Run " + console.bold("whaledoc update") + " to install it.");
                return 0;
            }

            withSpinner("Updating to %s...".formatted(latestVersion), () -> {
                updateService.install(latestVersion, CurrentExecutable.path());
                return latestVersion;
            });

            console.success("Updated WhaleDoc CLI from %s to %s".formatted(
                    currentVersion, console.bold(console.brand(latestVersion.toString()))));
            return 0;

        } catch (UpdateException e) {
            log.error("Update failed", e);
            console.error("Update failed: " + e.getMessage());
            return 1;
        }
    }

    private <T> T withSpinner(String message, Callable<T> action) {

        Spinner spinner = console.spinner(message);
        spinner.start();

        try {
            return action.call();

        } catch (UpdateException e) {
            throw e;

        } catch (Exception e) {
            throw new UpdateException(e.getMessage(), e);

        } finally {
            spinner.stop();
        }
    }
}
