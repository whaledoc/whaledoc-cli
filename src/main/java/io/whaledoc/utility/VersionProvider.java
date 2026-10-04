package io.whaledoc.utility;

import io.whaledoc.WhaleDocCli;
import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
        String version = WhaleDocCli.class
                .getPackage()
                .getImplementationVersion();

        return new String[]{
                "WhaleDoc version " + version
        };
    }
}

