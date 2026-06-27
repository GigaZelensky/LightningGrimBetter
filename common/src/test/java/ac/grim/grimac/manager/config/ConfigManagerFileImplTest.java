package ac.grim.grimac.manager.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerFileImplTest {

    @Test
    void addsMissingLightningCombatPunishmentGroups() {
        String input = """
                Punishments:
                  Reach:
                    remove-violations-after: 450
                    checks:
                      - "Reach"
                      - "Hitboxes"
                    commands:
                      - "1:1 [log]"
                """;

        String output = ConfigManagerFileImpl.ensureLightningCombatPunishmentGroups(input);

        assertTrue(output.contains("  WallHit:\n"));
        assertTrue(output.contains("      - \"WallHit\"\n"));
        assertTrue(output.contains("  EntityPierce:\n"));
        assertTrue(output.contains("      - \"EntityPierce\"\n"));
        assertTrue(output.contains("  Reach:\n"));
    }

    @Test
    void doesNotDuplicateExistingGroups() {
        String input = """
                Punishments:
                  WallHit:
                    remove-violations-after: 1
                    checks:
                      - "WallHit"
                    commands:
                      - "1:1 [alert]"
                  EntityPierce:
                    remove-violations-after: 1
                    checks:
                      - "EntityPierce"
                    commands:
                      - "1:1 [alert]"
                """;

        String output = ConfigManagerFileImpl.ensureLightningCombatPunishmentGroups(input);

        assertEquals(input, output);
    }

    @Test
    void ignoresNonPunishmentYaml() {
        String input = """
                alerts:
                  print-to-console: true
                """;

        assertEquals(input, ConfigManagerFileImpl.ensureLightningCombatPunishmentGroups(input));
    }
}
