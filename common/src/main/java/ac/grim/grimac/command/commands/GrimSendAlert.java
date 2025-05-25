package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.argument.StringArgument;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;

/**
 * /grim sendalert &lt;MiniMessage&gt;
 *
 * Everything typed after the word <b>sendalert</b> is forwarded untouched to MiniMessage,
 * so click / hover tags and line-breaks work exactly like they do in config values.
 */
public final class GrimSendAlert implements BuildableCommand {

    @Override
    public void register(@NonNull CommandManager<Sender> manager) {
        manager.command(
                manager.commandBuilder("grim", "grimac")
                        .literal(
                                "sendalert",
                                Description.of("Broadcast a MiniMessage alert to all staff"))
                        // Dummy greedy arg so Cloud accepts the rest of the line
                        .argument(StringArgument.greedy("message"))
                        .permission("grim.sendalert")
                        .handler(this::handleSendAlert));
    }

    private void handleSendAlert(@NonNull CommandContext<Sender> ctx) {

        // Grab the full, untouched command line ------------------------------
        String rawInput;
        try {
            rawInput = (String) ctx.getClass().getMethod("rawInput").invoke(ctx);
        } catch (Exception ex) {
            ctx.sender().sendMessage("§cYour Cloud build lacks rawInput(); /grim sendalert disabled.");
            return;
        }

        // Slice everything that follows the literal word "sendalert" ---------
        int keyword = rawInput.toLowerCase().indexOf("sendalert");
        if (keyword < 0) {
            ctx.sender().sendMessage("§cUnable to parse alert text.");
            return;
        }
        String msg = rawInput.substring(keyword + "sendalert".length()).trim();
        if (msg.isEmpty()) {
            ctx.sender().sendMessage("§cYou must provide the MiniMessage text.");
            return;
        }

        // Strip optional wrapping quotes -------------------------------------
        if (msg.length() > 1 &&
                ((msg.startsWith("\"") && msg.endsWith("\"")) ||
                 (msg.startsWith("'")  && msg.endsWith("'")))) {
            msg = msg.substring(1, msg.length() - 1);
        }

        // Convert to Adventure component and fire the alert ------------------
        Component component =
                MessageUtil.replacePlaceholders(
                        /* GrimPlayer */ null,
                        MessageUtil.miniMessage(msg));

        GrimAPI.INSTANCE.getAlertManager().sendAlert(component, null);
    }
}