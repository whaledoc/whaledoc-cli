package io.whaledoc.commands.update;

import io.whaledoc.config.ApplicationConfig;
import io.whaledoc.exceptions.UpdateException;
import io.whaledoc.update.CurrentExecutable;
import io.whaledoc.update.UpdateService;
import io.whaledoc.update.Version;
import io.whaledoc.utility.Spinner;
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

    @Override
    public Integer call() {

        if (!CurrentExecutable.isNativeImage()) {
            System.out.println("! 'whaledoc update' only works for an installed WhaleDoc CLI, not when running from a JAR.");
            return 1;
        }

        ApplicationConfig config = new ApplicationConfig();
        UpdateService updateService = UpdateService.create(config.getReleasesUrl());
        Version currentVersion = Version.parse(config.getVersion());

        try {
            Version latestVersion = withSpinner("Checking for updates...", updateService::fetchLatestVersion);

            if (!latestVersion.isNewerThan(currentVersion)) {
                System.out.println("> WhaleDoc CLI %s is up to date.".formatted(currentVersion));
                return 0;
            }

            if (checkOnly) {
                System.out.println("> A new version is available: %s -> %s".formatted(currentVersion, latestVersion));
                System.out.println("  Run 'whaledoc update' to install it.");
                return 0;
            }

            withSpinner("Updating to %s...".formatted(latestVersion), () -> {
                updateService.install(latestVersion, CurrentExecutable.path());
                return latestVersion;
            });

            System.out.println("> Updated WhaleDoc CLI from %s to %s".formatted(currentVersion, latestVersion));
            return 0;

        } catch (UpdateException e) {
            log.error("Update failed", e);
            System.out.println("! Update failed: " + e.getMessage());
            return 1;
        }
    }

    private static <T> T withSpinner(String message, Callable<T> action) {

        Spinner spinner = new Spinner(message);
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
