package ac.grim.grimac.command;

import ac.grim.grimac.api.platform.sender.Sender;
import org.incendo.cloud.CommandManager;

public interface BuildableCommand {
    void register(CommandManager<Sender> manager);
}
