package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;

/**
 * /grim sendalert <MiniMessage>
 *
 * Everything typed after the literal word “sendalert” is forwarded
 * unchanged to the MiniMessage parser, so click / hover tags and
 * new-line markers work exactly like they do in config values.
 */
public class GrimSendAlert implements BuildableCommand {

    @Override
    public void register(@NonNull CommandManager<Sender> manager) {
        manager.command(
                manager.commandBuilder("grim", "grimac")
                        .literal(
                                "sendalert",
                                Description.of("Broadcast a MiniMessage alert to all staff"))
                        .permission("grim.sendalert")
                        .handler(this::handleSendAlert));
    }

    private void handleSendAlert(@NonNull CommandContext<Sender> ctx) {
        // Grab the full, untouched command line. Cloud’s API changed names
        // between versions, so fall back to reflection if rawInput() isn’t present.
        String rawInput;
        try {
            rawInput = (String) ctx.getClass().getMethod("rawInput").invoke(ctx);
        } catch (Exception ignored) {
            ctx.sender().sendMessage("§cYour Cloud build lacks rawInput(); /grim sendalert disabled.");
            return;
        }

        // Locate the text that follows “sendalert”.
        int keyword = rawInput.toLowerCase().indexOf("sendalert");
        if (keyword < 0) {
            ctx.sender().sendMessage("§cUnable to parse alert text.");
            return;
        }
        int firstSpace = rawInput.indexOf(' ', keyword + "sendalert".length());
        if (firstSpace < 0 || firstSpace + 1 >= rawInput.length()) {
            ctx.sender().sendMessage("§cYou must provide the MiniMessage text.");
            return;
        }
        String message = rawInput.substring(firstSpace + 1).trim();

        // Strip optional surrounding quotes so staff can paste either
        // /grim sendalert "<click:...>"   or   /grim sendalert '<click:...>'
        if (message.length() > 1
                && ((message.startsWith("\"") && message.endsWith("\""))
                        || (message.startsWith("'") && message.endsWith("'")))) {
            message = message.substring(1, message.length() - 1);
        }

        // Convert to Adventure component and run Grim placeholders.
        Component alert =
                MessageUtil.replacePlaceholders(
                        /* GrimPlayer */ null, MessageUtil.miniMessage(message));

        GrimAPI.INSTANCE.getAlertManager().sendAlert(alert, null);
    }
}