package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.parser.standard.StringParser;

public final class GrimSendAlert implements BuildableCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public void register(@NonNull CommandManager<Sender> manager) {
        manager.command(
            manager.commandBuilder("grim", "grimac")
                   .literal("sendalert",
                            Description.of("Broadcast a MiniMessage alert to all staff"))
                   .required("message", StringParser.greedyStringParser())
                   .permission("grim.sendalert")
                   .handler(this::handleSendAlert));
    }

    private void handleSendAlert(@NonNull CommandContext<Sender> ctx) {
        String raw = ((String) ctx.get("message")).trim();

        // Strip optional wrapping quotes
        if (raw.length() > 1 &&
           ((raw.startsWith("\"") && raw.endsWith("\"")) ||
            (raw.startsWith("'")  && raw.endsWith("'")))) {
            raw = raw.substring(1, raw.length() - 1);
        }

        // 1. Deserialize MiniMessage -> Component (hover / click survive)
        Component component = MM.deserialize(raw);

        // 2. Apply %prefix% and any other placeholders the plugin knows
        component = MessageUtil.replacePlaceholders(/* GrimPlayer */ null, component);

        // 3. Ship it
        GrimAPI.INSTANCE.getAlertManager().sendAlert(component, null);
    }
}
