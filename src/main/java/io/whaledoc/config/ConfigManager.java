package io.whaledoc.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.whaledoc.exceptions.ConfigException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
public final class ConfigManager {

    private static final String APP_NAME = "WhaleDoc";
    private static final String CONFIG_FILE_NAME = "config.json";

    private final ObjectMapper objectMapper;
    private final Path configDirectory;
    private final Path configFile;

    public ConfigManager(ObjectMapper objectMapper, Path configDirectory) {

        this.objectMapper = objectMapper;
        this.configDirectory = configDirectory;
        this.configFile = configDirectory.resolve(CONFIG_FILE_NAME);
    }

    public static ConfigManager forCurrentUser(ObjectMapper objectMapper) {
        return new ConfigManager(objectMapper, resolveConfigDirectory());
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

            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(configFile.toFile(), config);

        } catch (IOException e) {
            throw new ConfigException("Unable to save WhaleDoc configuration.", e);
        }
    }

    private static Path resolveConfigDirectory() {

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");

            if (appData == null || appData.isBlank()) {
                throw new ConfigException("APPDATA environment variable is not available.");
            }

            return Path.of(appData, APP_NAME);
        }

        if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), ".config", "whaledoc");
        }

        if (os.contains("linux")) {
            String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");

            if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
                return Path.of(xdgConfigHome, "whaledoc");
            }

            return Path.of(System.getProperty("user.home"), ".config", "whaledoc");
        }

        throw new ConfigException("Unsupported operating system: " + os);
    }
}
