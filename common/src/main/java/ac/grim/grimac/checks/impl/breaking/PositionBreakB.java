package ac.grim.grimac.checks.impl.breaking;

import ac.grim.grimac.api.packet.player.enums.DiggingAction;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.BlockBreakCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockBreak;
import ac.grim.grimac.api.packet.world.enums.BlockFace;

@CheckData(name = "PositionBreakB", experimental = true)
public class PositionBreakB extends Check implements BlockBreakCheck {
    private final int releaseFace = player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_8) ? 0 : 255;
    private BlockFace lastFace;

    public PositionBreakB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action == DiggingAction.START_DIGGING) {
            if (blockBreak.face == lastFace) {
                lastFace = null;
            }
        }

        if (lastFace != null) {
            flagAndAlert("lastFace=" + lastFace + ", action=" + blockBreak.action);
        }

        if (blockBreak.action == DiggingAction.CANCELLED_DIGGING) {
            lastFace = blockBreak.faceId == releaseFace ? null : blockBreak.face;
        }
    }
}
