package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A semantic version such as {@code 1.2.3} or {@code 1.3.0-beta.1}.
 */
public record Version(int major, int minor, int patch, String preRelease) implements Comparable<Version> {

    private static final Pattern PATTERN = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?");

    private static final Comparator<Version> ORDER = Comparator
            .comparingInt(Version::major)
            .thenComparingInt(Version::minor)
            .thenComparingInt(Version::patch)
            .thenComparing(Version::preRelease, Version::comparePreRelease);

    public static Version parse(String value) {

        Matcher matcher = PATTERN.matcher(value.strip());

        if (!matcher.matches()) {
            throw new UpdateException("Invalid version: " + value);
        }

        return new Version(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                matcher.group(4)
        );
    }

    public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Version other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {

        String version = "%d.%d.%d".formatted(major, minor, patch);

        if (preRelease == null) {
            return version;
        }

        return version + "-" + preRelease;
    }

    // A release is newer than any of its pre-releases, e.g. 1.2.0 > 1.2.0-beta.1
    private static int comparePreRelease(String first, String second) {

        if (first == null || second == null) {
            return first == null ? (second == null ? 0 : 1) : -1;
        }

        return first.compareTo(second);
    }
}
