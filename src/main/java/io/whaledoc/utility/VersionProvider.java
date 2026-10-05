package io.whaledoc.utility;

import io.whaledoc.config.ApplicationConfig;
import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
        // Read from the build-time filtered application.yml: a native image has no JAR manifest
        String version = new ApplicationConfig().getVersion();

        return new String[]{
                "WhaleDoc version " + version
        };
    }
}

