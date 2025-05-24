package ac.grim.boar.anticheat.util.block.specific;

import ac.grim.boar.anticheat.player.BoarPlayer;
import org.geysermc.geyser.item.Items;

public class PowderSnowBlock {
    public static boolean canEntityWalkOnPowderSnow(final BoarPlayer player) {
        return player.compensatedInventory.translate(player.compensatedInventory.armorContainer.get(3).getData()).getId() == Items.LEATHER_BOOTS.javaId();
    }
}
