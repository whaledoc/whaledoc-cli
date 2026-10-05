package io.whaledoc.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.whaledoc.exceptions.ConfigException;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;

@Getter
public final class ApplicationConfig {

    private static final String CONFIG_FILE = "application.yml";

    private final String apiUrl;
    private final String version;

    public ApplicationConfig() {

        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

        try (InputStream inputStream = ApplicationConfig.class
                .getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {

            if (inputStream == null) {
                throw new ConfigException("Unable to find " + CONFIG_FILE);
            }

            JsonNode config = objectMapper.readTree(inputStream);
            this.apiUrl = config.at("/whaledoc/api/url").asText();
            this.version = config.at("/whaledoc/version").asText();

        } catch (IOException e) {
            throw new ConfigException("Unable to load application configuration.", e);
        }
    }

    public String apiUrl() {
        return apiUrl;
    }
}
