package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.parser.exception.ParsingException;
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
                   .literal(
                       "sendalert",
                       Description.of("Broadcast an alert (MiniMessage or legacy) to all staff"))
                   .required("message", StringParser.greedyStringParser())
                   .permission("grim.sendalert")
                   .handler(this::handleSendAlert)
        );
    }

    private void handleSendAlert(@NonNull CommandContext<Sender> ctx) {
        // Grab the raw, greedy argument
        String raw = ((String) ctx.get("message")).trim();

        // Allow optional wrapping quotes for ease of use
        if (raw.length() > 1 &&
           ((raw.startsWith("\"") && raw.endsWith("\"")) ||
            (raw.startsWith("'")  && raw.endsWith("'")))) {
            raw = raw.substring(1, raw.length() - 1);
        }

        Component component;

        /*
         * 1) First attempt: treat the whole thing as MiniMessage.
         *    If the string contains legacy ampersand codes, bad tags, etc.,
         *    MiniMessage will throw a ParsingException -> we catch and fall back.
         */
        try {
            component = MM.deserialize(raw);                          // MiniMessage parse
            component = MessageUtil.replacePlaceholders(null, component); // swap %placeholders%
        } catch (ParsingException ex) {
            // 2) Fallback: apply placeholders on the raw string, then legacy-parse
            String replaced = MessageUtil.replacePlaceholders(null, raw);
            component = MessageUtil.miniMessage(replaced);            // original pipeline
        }

        // Fire it off – same signature you were already using
        GrimAPI.INSTANCE.getAlertManager().sendAlert(component, null);
    }
}
