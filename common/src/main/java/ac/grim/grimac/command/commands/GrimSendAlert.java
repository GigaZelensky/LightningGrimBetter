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

    // IMPORTANT: Build MiniMessage with legacyColors() enabled.
    // This allows it to parse both MiniMessage tags AND legacy & codes
    // within the same string.
    private static final MiniMessage MM = MiniMessage.builder()
                                                     .legacyColors()
                                                     .build();

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

        // With legacyColors() enabled in the MM instance,
        // MM.deserialize will now correctly handle:
        // - Pure MiniMessage: "<red>Hello</red>"
        // - Pure Legacy: "&4Hello"
        // - Mixed: "<hover:show_text:"&cLegacy">MiniMessage &bMixed</hover>"
        // without throwing the reported exception.
        Component component = MM.deserialize(replaced);

        GrimAPI.INSTANCE.getAlertManager().sendAlert(component, null);
    }
}
