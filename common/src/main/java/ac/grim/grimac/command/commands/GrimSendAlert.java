package ac.grim.grimac.command.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.command.BuildableCommand;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage; // For the strict MiniMessage parser
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.parser.standard.StringParser;

public final class GrimSendAlert implements BuildableCommand {

    // This is the strict MiniMessage parser from your "First New Code".
    // It handles pure MiniMessage tags but will throw an exception
    // if it finds legacy codes mixed within its tags.
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

        // Strip optional wrapping quotes (from your "First New Code")
        if (raw.length() > 1 &&
           ((raw.startsWith("\"") && raw.endsWith("\"")) ||
            (raw.startsWith("'")  && raw.endsWith("'")))) {
            raw = raw.substring(1, raw.length() - 1);
        }

        String replaced = MessageUtil.replacePlaceholders((Sender) null, raw);

        Component finalComponent;

        try {
            // Attempt 1: Parse with the STRICT MiniMessage parser (MM.deserialize)
            Component miniMessageAttempt = MM.deserialize(replaced);

            // Check if the strict parser actually did any MiniMessage-specific work.
            // If `miniMessageAttempt` is just plain text of the input `replaced`,
            // it means no MiniMessage tags were effectively processed.
            // This is the case for inputs like "&4Hello".
            if (miniMessageAttempt.equals(Component.text(replaced))) {
                // MM.deserialize did nothing special (e.g., for "&4Hello").
                // In this specific case, we *must* use MessageUtil.miniMessage()
                // because it's known to handle pure legacy codes from your "Original Code".
                finalComponent = MessageUtil.miniMessage(replaced);
            } else {
                // MM.deserialize successfully processed MiniMessage tags (e.g., "<red>Hello</red>").
                // Use its result.
                finalComponent = miniMessageAttempt;
            }
        } catch (Exception e) {
            // Attempt 2 (Fallback): An exception occurred with MM.deserialize.
            // This happens if MiniMessage tags were present, but they contained
            // legacy codes that the strict MM parser couldn't handle (e.g., <hover:show_text:"&d...">).
            // As per your "First New Code's" behavior, MessageUtil.miniMessage()
            // CAN successfully parse these complex mixed strings.
            finalComponent = MessageUtil.miniMessage(replaced);
        }

        GrimAPI.INSTANCE.getAlertManager().sendAlert(finalComponent, null);
    }
}
