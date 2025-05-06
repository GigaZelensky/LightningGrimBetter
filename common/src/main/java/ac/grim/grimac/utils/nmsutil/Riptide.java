package ac.grim.grimac.utils.nmsutil;

import ac.grim.grimac.api.packet.item.PacketItemStack;
import ac.grim.grimac.api.packet.item.PacketItemTypes;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.math.GrimMath;
import ac.grim.grimac.api.math.Vector3dm;
import com.github.retrooper.packetevents.PacketEvents;
import ac.grim.grimac.api.packet.item.PacketEnchantmentTypes;

public class Riptide {
    public static Vector3dm getRiptideVelocity(GrimPlayer player) {
        PacketItemStack main = player.getInventory().getHeldItem();
        PacketItemStack off = player.getInventory().getOffHand();

        int j;
        if (main.getType() == PacketItemTypes.TRIDENT) {
            j = main.getEnchantmentLevel(PacketEnchantmentTypes.RIPTIDE, PacketEvents.getAPI().getServerManager().getVersion().toClientVersion().getProtocolVersion());
        } else if (off.getType() == PacketItemTypes.TRIDENT) {
            j = off.getEnchantmentLevel(PacketEnchantmentTypes.RIPTIDE, PacketEvents.getAPI().getServerManager().getVersion().toClientVersion().getProtocolVersion());
        } else {
            return new Vector3dm(); // Can't riptide
        }

        float f7 = player.xRot;
        float f = player.yRot;
        float f1 = -player.trigHandler.sin(GrimMath.radians(f7)) * player.trigHandler.cos(GrimMath.radians(f));
        float f2 = -player.trigHandler.sin(GrimMath.radians(f));
        float f3 = player.trigHandler.cos(GrimMath.radians(f7)) * player.trigHandler.cos(GrimMath.radians(f));
        float f4 = (float) Math.sqrt(f1 * f1 + f2 * f2 + f3 * f3);
        float f5 = 3f * ((1f + j) / 4f);
        f1 = f1 * (f5 / f4);
        f2 = f2 * (f5 / f4);
        f3 = f3 * (f5 / f4);

        // If the player collided vertically with the 1.199999F pushing movement, then the Y additional movement was added
        // (We switched the order around as our prediction engine isn't designed for the proper implementation)
        if (player.verticalCollision) return new Vector3dm(f1, 0, f3);

        return new Vector3dm(f1, f2, f3);
    }
}
