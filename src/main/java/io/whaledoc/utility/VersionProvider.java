package io.whaledoc.utility;

import lombok.RequiredArgsConstructor;
import picocli.CommandLine;

@RequiredArgsConstructor
public class VersionProvider implements CommandLine.IVersionProvider {

    // Comes from the build-time filtered application.yml: a native image has no JAR manifest
    private final String version;

    @Override
    public String[] getVersion() {

        return new String[]{
                "WhaleDoc version " + version
        };
    }
}
