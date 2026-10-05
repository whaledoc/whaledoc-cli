package io.whaledoc.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.whaledoc.exceptions.ConfigException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Build-time settings from application.yml, which Maven fills in when the CLI is built.
 */
public record ApplicationConfig(String apiUrl, String releasesUrl, String version) {

    private static final String CONFIG_FILE = "application.yml";

    public static ApplicationConfig load() {

        try (InputStream inputStream = ApplicationConfig.class
                .getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {

            if (inputStream == null) {
                throw new ConfigException("Unable to find " + CONFIG_FILE);
            }

            JsonNode config = new ObjectMapper(new YAMLFactory()).readTree(inputStream);

            return new ApplicationConfig(
                    config.at("/whaledoc/api/url").asText(),
                    config.at("/whaledoc/releases/url").asText(),
                    config.at("/whaledoc/version").asText()
            );

        } catch (IOException e) {
            throw new ConfigException("Unable to load application configuration.", e);
        }
    }
}
