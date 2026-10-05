package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;
import java.util.Set;

/**
 * The operating systems and CPUs a native executable is released for, matching the release archive names.
 */
@Getter
@RequiredArgsConstructor
public enum Platform {

    LINUX_X64("linux-x64", ".tar.gz", "whaledoc"),
    LINUX_ARM64("linux-arm64", ".tar.gz", "whaledoc"),
    MACOS_X64("macos-x64", ".tar.gz", "whaledoc"),
    MACOS_ARM64("macos-arm64", ".tar.gz", "whaledoc"),
    WINDOWS_X64("windows-x64", ".zip", "whaledoc.exe");

    private static final Set<String> ARM_ARCHITECTURES = Set.of("aarch64", "arm64");

    private final String target;
    private final String archiveExtension;
    private final String executableName;

    public static Platform current() {
        return from(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    public static Platform from(String osName, String osArch) {

        String os = osName.toLowerCase(Locale.ROOT);
        boolean arm = ARM_ARCHITECTURES.contains(osArch.toLowerCase(Locale.ROOT));

        if (os.contains("win")) {
            return WINDOWS_X64;
        }

        if (os.contains("mac")) {
            return arm ? MACOS_ARM64 : MACOS_X64;
        }

        if (os.contains("linux")) {
            return arm ? LINUX_ARM64 : LINUX_X64;
        }

        throw new UpdateException("Updates are not supported on %s (%s).".formatted(osName, osArch));
    }

    public String archiveName() {
        return "whaledoc-" + target + archiveExtension;
    }

    public boolean isWindows() {
        return this == WINDOWS_X64;
    }
}
