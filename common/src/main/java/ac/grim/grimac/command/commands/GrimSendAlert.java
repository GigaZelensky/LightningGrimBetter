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
import org.incendo.cloud.parser.standard.StringParser;

/**
 * /grim sendalert <MiniMessage>
 *
 * Everything after the word <b>sendalert</b> is parsed with the Cloud
 * greedy string parser, so spaces and tags are preserved. No quoting or
 * escaping needed—just paste the MiniMessage exactly as it appears in
 * your config file.
 */
public final class GrimSendAlert implements BuildableCommand {

    @Override
    public void register(@NonNull CommandManager<Sender> manager) {
        manager.command(
                manager.commandBuilder("grim", "grimac")
                        .literal(
                                "sendalert",
                                Description.of("Broadcast a MiniMessage alert to all staff"))
                        .required("message", StringParser.greedyStringParser())
                        .permission("grim.sendalert")
                        .handler(this::handleSendAlert));
    }

    private void handleSendAlert(@NonNull CommandContext<Sender> ctx) {
        // The entire remainder of the command line
        String msg = ctx.get("message");

        // Strip OPTIONAL wrapping quotes, if the sender added them
        if (msg.length() > 1 &&
                ((msg.startsWith("\"") && msg.endsWith("\"")) ||
                 (msg.startsWith("'")  && msg.endsWith("'")))) {
            msg = msg.substring(1, msg.length() - 1);
        }

        Component component =
                MessageUtil.replacePlaceholders(
                        /* GrimPlayer */ null,
                        MessageUtil.miniMessage(msg));

        GrimAPI.INSTANCE.getAlertManager().sendAlert(component, null);
    }
}