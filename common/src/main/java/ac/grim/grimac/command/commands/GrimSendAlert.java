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

import java.util.regex.Pattern; // Import Pattern

public final class GrimSendAlert implements BuildableCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Regex to find & followed by a hex digit (0-9, a-f, A-F) or a formatting code (k,l,m,n,o,r)
    // This avoids replacing literal '&' that are not color codes.
    private static final Pattern LEGACY_COLOR_CODE_PATTERN = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

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

    private String translateLegacyColors(String text) {
        if (text == null) {
            return null;
        }
        // Replace & codes with § codes
        return LEGACY_COLOR_CODE_PATTERN.matcher(text).replaceAll("\u00A7$1");
    }

    private void handleSendAlert(@NonNull CommandContext<Sender> ctx) {
        String raw = ((String) ctx.get("message")).trim();

        if (raw.length() > 1 &&
           ((raw.startsWith("\"") && raw.endsWith("\"")) ||
            (raw.startsWith("'")  && raw.endsWith("'")))) {
            raw = raw.substring(1, raw.length() - 1);
        }

        String replacedPlaceholders = MessageUtil.replacePlaceholders((Sender) null, raw);

        // Translate & to § BEFORE any MiniMessage parsing attempt.
        // This helps MiniMessage's strict parser correctly interpret legacy codes
        // embedded within its tag attributes (like hover_text) if it doesn't
        // throw an exception for other reasons.
        String translatedLegacy = translateLegacyColors(replacedPlaceholders);

        Component finalComponent;

        try {
            // Attempt 1: Parse with the STRICT MiniMessage parser (MM.deserialize)
            // using the string that now has § for legacy codes.
            Component miniMessageAttempt = MM.deserialize(translatedLegacy);

            // If MM.deserialize produced a component identical to plain text of the *translatedLegacy* string,
            // it means no MiniMessage tags were effectively processed (or it was just plain text).
            // This is the case for inputs like "§4Hello" (originally "&4Hello").
            if (miniMessageAttempt.equals(Component.text(translatedLegacy))) {
                // MM.deserialize did nothing special.
                // Fall back to MessageUtil.miniMessage() with the *original placeholder-replaced string*
                // (before & -> § translation) as it might have its own specific & handling.
                // OR, if MessageUtil.miniMessage() also expects §, use translatedLegacy here too.
                // For now, assuming MessageUtil.miniMessage() handles '&' correctly as per original code:
                finalComponent = MessageUtil.miniMessage(replacedPlaceholders);
            } else {
                // MM.deserialize successfully processed MiniMessage tags.
                // Use its result (which was parsed from the string with § codes).
                finalComponent = miniMessageAttempt;
            }
        } catch (Exception e) {
            // Attempt 2 (Fallback): An exception occurred with MM.deserialize.
            // This happens if MiniMessage tags were present, but they contained
            // something the strict MM parser couldn't handle (even after & -> §).
            // (Though less likely for legacy codes now, could be other malformed MiniMessage).
            // As per your "First New Code's" behavior, MessageUtil.miniMessage()
            // CAN successfully parse these complex mixed strings.
            // Use the original placeholder-replaced string here.
            finalComponent = MessageUtil.miniMessage(replacedPlaceholders);
        }

        GrimAPI.INSTANCE.getAlertManager().sendAlert(finalComponent, null);
    }
}
