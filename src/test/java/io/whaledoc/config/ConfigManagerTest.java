package io.whaledoc.config;

import io.whaledoc.exceptions.ConfigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ConfigManagerTest {

    @TempDir
    private Path tempDir;

    private Path configDirectory;
    private ConfigManager configManager;

    @BeforeEach
    void setUp() {
        configDirectory = tempDir.resolve("whaledoc");
        configManager = new ConfigManager(configDirectory);
    }

    @Test
    void shouldCreateConfigWithCliIdWhenConfigFileDoesNotExist() {

        // given
        Path configFile = configDirectory.resolve("config.json");

        // when
        WhaleDocConfig actualConfig = configManager.load();

        // then
        assertThat(actualConfig.cliId()).isNotBlank();
        assertThat(actualConfig.accessToken()).isNull();
        assertThat(configFile).exists();
    }

    @Test
    void shouldKeepCliIdWhenConfigIsLoadedAgain() {

        // given
        WhaleDocConfig expectedConfig = configManager.load();

        // when
        WhaleDocConfig actualConfig = new ConfigManager(configDirectory).load();

        // then
        assertThat(actualConfig).usingRecursiveComparison().isEqualTo(expectedConfig);
    }

    @Test
    void shouldReturnSavedConfigWhenConfigWasSaved() {

        // given
        WhaleDocConfig expectedConfig = createLoggedInConfig();
        configManager.saveToFile(expectedConfig);

        // when
        WhaleDocConfig actualConfig = configManager.load();

        // then
        assertThat(actualConfig).usingRecursiveComparison().isEqualTo(expectedConfig);
    }

    @Test
    void shouldThrowConfigExceptionWhenConfigFileIsInvalid() throws IOException {

        // given
        givenConfigFileContains("{ not json");

        // when
        Throwable thrown = catchThrowable(() -> configManager.load());

        // then
        assertThat(thrown)
                .isInstanceOf(ConfigException.class)
                .hasMessage("Unable to load WhaleDoc configuration.");
    }

    private void givenConfigFileContains(String content) throws IOException {

        Files.createDirectories(configDirectory);
        Files.writeString(configDirectory.resolve("config.json"), content);
    }

    private WhaleDocConfig createLoggedInConfig() {

        return WhaleDocConfig.builder()
                .cliId("3f1b2c4d-5e6f-4a1b-9c2d-7e8f9a0b1c2d")
                .accessToken("access-token")
                .build();
    }
}
