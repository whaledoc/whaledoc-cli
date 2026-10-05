package io.whaledoc;

import io.whaledoc.commands.auth.LoginCommand;
import io.whaledoc.commands.auth.LogoutCommand;
import io.whaledoc.commands.update.UpdateCommand;
import io.whaledoc.commands.webhook.ListenCommand;
import io.whaledoc.update.Version;
import io.whaledoc.utility.VersionProvider;
import picocli.CommandLine;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Creates the commands with the shared objects from the application context.
 *
 * <p>Picocli creates every subcommand on each run, so commands only receive existing objects here
 * and never build their own clients. This also avoids reflection for these classes in the native image.
 */
public final class CommandFactory implements CommandLine.IFactory {

    private final Map<Class<?>, Supplier<Object>> factories;

    public CommandFactory(ApplicationContext context) {

        this.factories = Map.of(
                LoginCommand.class, () -> new LoginCommand(context.configManager(), context.authClient()),
                LogoutCommand.class, () -> new LogoutCommand(context.configManager(), context.authClient()),
                ListenCommand.class, () -> new ListenCommand(context.configManager(), context.webhookClient()),
                UpdateCommand.class, () -> new UpdateCommand(context.updateService(), Version.parse(context.config().version())),
                VersionProvider.class, () -> new VersionProvider(context.config().version())
        );
    }

    @Override
    public <K> K create(Class<K> type) throws Exception {

        Supplier<Object> factory = factories.get(type);

        if (factory == null) {
            return CommandLine.defaultFactory().create(type);
        }

        return type.cast(factory.get());
    }
}
