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
import org.incendo.cloud.description.Description; // Keep Description import
import org.incendo.cloud.parser.standard.StringParser;

public final class GrimSendAlert implements BuildableCommand {

    // Keep static MiniMessage instance for efficiency
    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public void register(@NonNull CommandManager<Sender> manager) {
        manager.command(
            manager.commandBuilder("grim", "grimac")
                   .literal(
                       "sendalert",
                       Description.of("Broadcast an alert (MiniMessage or legacy) to all staff")) // Keep description
                   .permission("grim.sendalert")
                   .required("message", StringParser.greedyStringParser())
                   .handler(this::handleSendAlert)
        );
    }

    private void handleSendAlert(@NonNull CommandContext<Sender> ctx) {
        String raw = ((String) ctx.get("message")).trim();

        // Strip optional wrapping quotes (keep this new functionality)
        if (raw.length() > 1 &&
           ((raw.startsWith("\"") && raw.endsWith("\"")) ||
            (raw.startsWith("'")  && raw.endsWith("'")))) {
            raw = raw.substring(1, raw.length() - 1);
        }

        /* -----------------------------------------------------------
         *  Replace %placeholders% BEFORE any parsing.
         *  Casting the first arg to (Sender) resolves the overload
         *  ambiguity in MessageUtil.replacePlaceholders(..., String).
         * ----------------------------------------------------------- */
        String replaced = MessageUtil.replacePlaceholders((Sender) null, raw);

        Component component;

        // Always attempt MiniMessage parsing first.
        // MM.deserialize won't throw an exception for legacy codes,
        // it will just treat them as literal text.
        Component miniMessageParsed = MM.deserialize(replaced);

        // Also parse using the legacy method, which handles & codes.
        Component legacyParsed = MessageUtil.miniMessage(replaced);

        // If the MiniMessage-parsed component is *exactly* the same as a plain text
        // component of the original string, it means MiniMessage didn't apply
        // any formatting (i.e., no MiniMessage tags were found or valid).
        // In this case, we prefer the legacy-parsed component.
        if (miniMessageParsed.equals(Component.text(replaced))) {
            // MiniMessage didn't do anything special, so use the legacy formatted message.
            component = legacyParsed;
        } else {
            // MiniMessage successfully processed some tags, so use its output.
            component = miniMessageParsed;
        }

        GrimAPI.INSTANCE.getAlertManager().sendAlert(component, null);
    }
}
