package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

/**
 * /grim sendalert <miniMessage>
 *
 * We bypass Cloud's argument tokenizer by reading the raw input line
 * and slicing everything that follows the literal sub‑command "sendalert".
 * That way staff can paste any MiniMessage—including <> click/hover tags,
 * quotes, spaces, and line‑breaks—without needing to escape them.
 */
public class GrimSendAlert implements BuildableCommand {

    @Override
    public void register(@NonNull CommandManager<Sender> commandManager) {
        commandManager.command(
                commandManager.commandBuilder("grim", "grimac")
                        .literal("sendalert")
                        .permission("grim.sendalert")
                        // no arguments; we parse the tail manually
                        .handler(this::handleSendAlert)
        );
    }

    private void handleSendAlert(@NonNull CommandContext<Sender> context) {

        // Full line, e.g. "/grim sendalert <click:...>"
        final String raw = context.rawInput();

        // Locate the sub‑command token "sendalert"
        final int idx = raw.toLowerCase().indexOf("sendalert");
        if (idx == -1) {
            context.sender().sendMessage("§cCould not parse alert text.");
            return;
        }

        // Everything after the token (trim leading whitespace)
        String msg = raw.substring(idx + "sendalert".length()).trim();

        // Strip optional surrounding quotes so users can still wrap the message
        if (msg.length() > 1 &&
                ((msg.startsWith(""") && msg.endsWith(""")) ||
                 (msg.startsWith("'") && msg.endsWith("'")))) {
            msg = msg.substring(1, msg.length() - 1);
        }

        msg = MessageUtil.replacePlaceholders(null, msg);
        Component component = MessageUtil.miniMessage(msg);
        GrimAPI.INSTANCE.getAlertManager().sendAlert(component, null);
    }
}
