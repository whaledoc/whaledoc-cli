package io.whaledoc.update;

import io.whaledoc.exceptions.UpdateException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The SHA-256 checksums published with a release, in {@code sha256sum} format: {@code <hash>  <file name>}. The first
 * line names the release they belong to ({@code # whaledoc v1.2.0}), so signed checksums of one release can't pass
 * for another's.
 */
public record Checksums(String release, Map<String, String> sha256ByFileName) {

    private static final String COMMENT = "#";
    private static final String RELEASE_LINE = "# whaledoc ";
    private static final String NO_RELEASE = "";

    public static Checksums parse(String content) {

        String release = content.lines()
                .findFirst()
                .map(String::strip)
                .filter(line -> line.startsWith(RELEASE_LINE))
                .map(line -> line.substring(RELEASE_LINE.length()).strip())
                .orElse(NO_RELEASE);

        Map<String, String> checksums = content.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT))
                .map(line -> line.split("\\s+\\*?", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[1], parts -> parts[0], (first, second) -> first));

        return new Checksums(release, checksums);
    }

    public void verifyRelease(Version version) {

        String expected = "v" + version;

        if (!release.equals(expected)) {
            throw new UpdateException("The checksums don't belong to %s. The update was stopped and nothing was changed."
                    .formatted(expected));
        }
    }

    public void verify(Path file) {

        String fileName = file.getFileName().toString();
        String expected = sha256ByFileName.get(fileName);

        if (expected == null) {
            throw new UpdateException("No checksum published for " + fileName + ".");
        }

        if (!expected.equalsIgnoreCase(sha256(file))) {
            throw new UpdateException("Checksum mismatch for " + fileName + ". The download may be corrupted; please try again.");
        }
    }

    private static String sha256(Path file) {

        try (DigestInputStream input = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {

            input.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(input.getMessageDigest().digest());

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new UpdateException("Unable to verify " + file.getFileName() + ".", e);
        }
    }
}
