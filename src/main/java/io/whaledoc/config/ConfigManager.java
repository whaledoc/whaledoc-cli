package io.whaledoc.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.whaledoc.exceptions.ConfigException;
import io.whaledoc.webhook.WebhookSigner;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
public final class ConfigManager {

    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String TEMP_PREFIX = "config-";
    private static final String TEMP_SUFFIX = ".json.tmp";
    private static final Set<PosixFilePermission> OWNER_READ_WRITE = PosixFilePermissions.fromString("rw-------");

    private final ObjectMapper objectMapper;
    private final Path configDirectory;
    private final Path configFile;

    public ConfigManager(ObjectMapper objectMapper, Path configDirectory) {

        this.objectMapper = objectMapper;
        this.configDirectory = configDirectory;
        this.configFile = configDirectory.resolve(CONFIG_FILE_NAME);
    }

    public static ConfigManager forCurrentUser(ObjectMapper objectMapper) {
        return new ConfigManager(objectMapper, AppDirectories.forCurrentUser().config());
    }

    public WhaleDocConfig load() {

        try {
            Files.createDirectories(configDirectory);

            if (!Files.exists(configFile)) {
                return createDefaultConfig();
            }

            return objectMapper.readValue(configFile.toFile(), WhaleDocConfig.class);

        } catch (IOException e) {
            throw new ConfigException("Unable to load WhaleDoc configuration.", e);
        }
    }

    /** The secret forwarded events are signed with, made and saved the first time it is asked for. */
    public String webhookSecret() {

        WhaleDocConfig config = load();

        return Optional.ofNullable(config.webhookSecret()).orElseGet(this::newWebhookSecret);
    }

    /** Read again right before saving: another command may have made one meanwhile, and it is kept. */
    private String newWebhookSecret() {

        WhaleDocConfig latest = load();

        return Optional.ofNullable(latest.webhookSecret()).orElseGet(() -> {
            String secret = WebhookSigner.newSecret();
            saveToFile(latest.toBuilder().webhookSecret(secret).build());
            return secret;
        });
    }

    private WhaleDocConfig createDefaultConfig() {

        log.debug("Creating default config.");
        WhaleDocConfig config = WhaleDocConfig.builder()
                .cliId(UUID.randomUUID().toString())
                .build();

        saveToFile(config);
        return config;
    }

    public void saveToFile(WhaleDocConfig config) {

        try {
            Files.createDirectories(configDirectory);
            Path written = createPrivateFile();

            try {
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValue(written.toFile(), config);
                Files.move(written, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            } finally {
                Files.deleteIfExists(written);
            }

        } catch (IOException e) {
            throw new ConfigException("Unable to save WhaleDoc configuration.", e);
        }
    }

    /**
     * The new config is written next to the old one and moved over it in one step, so another command never reads a
     * half-written file. It holds the access token, so on macOS and Linux only the user may read it; on Windows, the
     * config directory under %APPDATA% is already private to the user. Replacing the file also tightens configs made
     * by earlier versions.
     */
    private Path createPrivateFile() throws IOException {

        if (!configDirectory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return Files.createTempFile(configDirectory, TEMP_PREFIX, TEMP_SUFFIX);
        }

        return Files.createTempFile(configDirectory, TEMP_PREFIX, TEMP_SUFFIX,
                PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE));
    }
}
