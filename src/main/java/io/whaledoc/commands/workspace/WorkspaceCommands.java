package io.whaledoc.commands.workspace;

import io.whaledoc.config.ConfigManager;
import io.whaledoc.console.Console;
import io.whaledoc.exceptions.ApiException;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/** What the workspace commands share: the saved login, and how a refusal reads. */
final class WorkspaceCommands {

    private static final int UNAUTHORIZED = 401;

    private WorkspaceCommands() {
    }

    /** The saved token; without one, tells the user to log in first. */
    static Optional<String> accessToken(ConfigManager configManager, Console console) {

        Optional<String> token = Optional.ofNullable(configManager.load().accessToken()).filter(StringUtils::isNotBlank);

        if (token.isEmpty()) {
            console.error("You're not logged in. Run " + console.bold("whaledoc login") + " first.");
        }

        return token;
    }

    /** A login that no longer works asks for a new one; anything else is the API's own sentence. */
    static String describe(ApiException e, Console console) {

        if (e.statusCode() == UNAUTHORIZED) {
            return "Your login is no longer valid. Run " + console.bold("whaledoc login") + " and try again.";
        }

        return e.getMessage();
    }
}
