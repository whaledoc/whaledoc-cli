package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The executable the CLI is currently running from.
 */
public final class CurrentExecutable {

    private CurrentExecutable() {
    }

    // GraalVM sets this property to "runtime" inside a native executable
    public static boolean isNativeImage() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    /**
     * Homebrew keeps installed formulas under a Cellar directory, e.g. /opt/homebrew/Cellar/whaledoc/1.2.0/bin.
     * Those installations must be updated through Homebrew, or it loses track of the installed version.
     */
    public static boolean isManagedByHomebrew(Path executable) {
        return executable.toString().replace('\\', '/').contains("/Cellar/");
    }

    public static Path path() {

        String command = ProcessHandle.current()
                .info()
                .command()
                .orElseThrow(() -> new UpdateException("Unable to determine where the WhaleDoc CLI is installed."));

        try {
            // Resolve symlinks so the real file is replaced, not the link pointing to it
            return Path.of(command).toRealPath();

        } catch (IOException e) {
            throw new UpdateException("Unable to determine where the WhaleDoc CLI is installed.", e);
        }
    }
}
