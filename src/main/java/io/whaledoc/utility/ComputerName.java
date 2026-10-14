package io.whaledoc.utility;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The name of this computer, such as "dev-laptop", so your list of sessions can tell your logins apart. Read from the
 * system without a network lookup: the {@code hostname} command, which Windows, macOS and Linux all have, else
 * {@code COMPUTERNAME} (Windows; upper case and at most 15 characters, so only a fallback) or {@code HOSTNAME}.
 * macOS's ".local" ending is left out.
 */
public final class ComputerName {

    private static final String LOCAL_SUFFIX = ".local";
    private static final long HOSTNAME_TIMEOUT_SECONDS = 2;

    private ComputerName() {
    }

    public static Optional<String> current() {

        return hostnameCommand()
                .filter(StringUtils::isNotBlank)
                .or(ComputerName::fromEnvironment)
                .map(String::strip)
                .map(ComputerName::withoutLocalSuffix)
                .filter(StringUtils::isNotBlank);
    }

    // The same system call as Go's os.Hostname(); unlike Java's InetAddress.getLocalHost() it never asks DNS, which on
    // macOS can stall for seconds when the name isn't in /etc/hosts
    private static Optional<String> hostnameCommand() {

        try {
            Process process = new ProcessBuilder("hostname").redirectErrorStream(true).start();

            // One short line of output never fills the pipe, so waiting before reading can't block
            if (!process.waitFor(HOSTNAME_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }

            if (process.exitValue() != 0) {
                return Optional.empty();
            }

            try (BufferedReader output = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

                return Optional.ofNullable(output.readLine());
            }

        } catch (IOException e) {
            return Optional.empty();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static Optional<String> fromEnvironment() {

        return Stream.of(System.getenv("COMPUTERNAME"), System.getenv("HOSTNAME"))
                .filter(StringUtils::isNotBlank)
                .findFirst();
    }

    private static String withoutLocalSuffix(String name) {

        return name.toLowerCase(Locale.ROOT).endsWith(LOCAL_SUFFIX)
                ? name.substring(0, name.length() - LOCAL_SUFFIX.length())
                : name;
    }
}
