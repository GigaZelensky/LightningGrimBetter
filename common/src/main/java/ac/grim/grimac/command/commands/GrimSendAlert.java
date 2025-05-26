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

    /** Re-use one MiniMessage instance – cheap and thread-safe. */
    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public void register(@NonNull CommandManager<Sender> manager) {
        manager.command(
            manager.commandBuilder("grim", "grimac")
                   .literal(
                       "sendalert",
                       Description.of("Broadcast an alert (MiniMessage or legacy) to all staff"))
                   .permission("grim.sendalert")
                   .required("message", StringParser.greedyStringParser())
                   .handler(this::handleSendAlert)
        );
    }

    private void handleSendAlert(@NonNull CommandContext<Sender> ctx) {
        String raw = ((String) ctx.get("message")).trim();

        // Allow optional wrapping quotes
        if (raw.length() > 1 &&
           ((raw.startsWith("\"") && raw.endsWith("\"")) ||
            (raw.startsWith("'")  && raw.endsWith("'")))) {
            raw = raw.substring(1, raw.length() - 1);
        }

        Component component;

        try {
            /* ---------- ① MiniMessage path ---------- */
            component = MM.deserialize(raw);                       // parse tags / click / hover / colours
            component = MessageUtil.replacePlaceholders(null, component); // swap %placeholders%
        } catch (Exception ignored) {
            /* ---------- ② Legacy fallback ---------- */
            String replaced = MessageUtil.replacePlaceholders(null, raw); // %prefix% etc.
            component = MessageUtil.miniMessage(replaced);               // legacy & colours
        }

        GrimAPI.INSTANCE.getAlertManager().sendAlert(component, null);
    }
}
