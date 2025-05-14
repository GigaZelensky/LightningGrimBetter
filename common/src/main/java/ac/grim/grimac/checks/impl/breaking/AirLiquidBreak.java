package ac.grim.grimac.checks.impl.breaking;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.packet.item.PacketItemTypes;
import ac.grim.grimac.api.packet.item.PacketStateType;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.world.PacketStateTypes;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.BlockBreakCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockBreak;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import org.checkerframework.checker.nullness.qual.NonNull;

@CheckData(name = "AirLiquidBreak", description = "Breaking a block that cannot be broken")
public class AirLiquidBreak extends Check implements BlockBreakCheck {
    public final boolean noFireHitbox = player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_15_2);
    private int lastTick;
    private boolean didLastFlag;
    // Initialize to non-null values to prevent NPE when checking for blockType properties and if position equals old position
    private @NonNull Vector3i lastBreakLoc = new Vector3i();
    private @NonNull PacketStateType lastBlockType = PacketStateTypes.AIR;

    public AirLiquidBreak(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action != DiggingAction.START_DIGGING && blockBreak.action != DiggingAction.FINISHED_DIGGING)
            return;

        final PacketStateType block = blockBreak.block.getType();

        // Fixes false from breaking kelp underwater
        // The client sends two start digging packets to the server both in the same tick. AirLiquidBreak gets called twice, doesn't false the first time, but falses the second
        // One ends up breaking the kelp, the other ends up doing nothing besides falsing this check because we think they're trying to mine water
        // I am explicitly making this patch as narrow and specific as possible to potentially discover other blocks that exhibit similar behaviour
        int newTick = GrimAPI.INSTANCE.getTickManager().currentTick;
        if (lastTick == newTick
                && lastBreakLoc.equals(blockBreak.position)
                && !didLastFlag
                && lastBlockType.getHardness() == 0.0F
                && lastBlockType.getBlastResistance() == 0.0F
                && block == PacketStateTypes.WATER
        ) return;
        lastTick = newTick;
        lastBreakLoc = blockBreak.position;
        lastBlockType = block;

        // the block does not have a hitbox
        boolean invalid = (block == PacketStateTypes.LIGHT && !(player.getInventory().getHeldItem() == PacketItemTypes.LIGHT || player.getInventory().getOffHand() == PacketItemTypes.LIGHT))
                || block.isAir()
                || block == PacketStateTypes.WATER
                || block == PacketStateTypes.LAVA
                || block == PacketStateTypes.BUBBLE_COLUMN
                || block == PacketStateTypes.MOVING_PISTON
                || block == PacketStateTypes.FIRE && noFireHitbox
                // or the client claims to have broken an unbreakable block
                || block.getHardness() == -1.0f && blockBreak.action == DiggingAction.FINISHED_DIGGING;

        if (invalid && flagAndAlert("block=" + block.getName() + ", type=" + blockBreak.action) && shouldModifyPackets()) {
            didLastFlag = true;
            blockBreak.cancel();
        } else {
            didLastFlag = false;
        }
    }
}
