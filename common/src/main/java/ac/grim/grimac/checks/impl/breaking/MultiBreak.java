package ac.grim.grimac.checks.impl.breaking;

import ac.grim.grimac.api.storage.verbose.VerboseSchema;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.BlockBreakCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import ac.grim.grimac.utils.anticheat.update.BlockBreak;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3i;

import java.util.ArrayList;
import java.util.List;

@CheckData(name = "MultiBreak", stableKey = "grim.breaking.multi_break", verboseVersion = 1, experimental = true)
public class MultiBreak extends Check implements BlockBreakCheck {
    public static final VerboseSchema V = VerboseSchema.of(
            "face:str", "lastFace:str", "pos:str", "lastPos:str");

    private final List<FlagData> flags = new ArrayList<>();
    private boolean hasBroken;
    private BlockFace lastFace;
    private Vector3i lastPos;

    public MultiBreak(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (blockBreak.action == DiggingAction.CANCELLED_DIGGING) {
            return;
        }

        if (hasBroken && (blockBreak.face != lastFace || !blockBreak.position.equals(lastPos))) {
            final String face = String.valueOf(blockBreak.face);
            final String previousFace = String.valueOf(lastFace);
            final String pos = MessageUtil.toUnlabledString(blockBreak.position);
            final String previousPos = MessageUtil.toUnlabledString(lastPos);
            final String verbose = "face=" + face + ", lastFace=" + previousFace
                    + ", pos=" + pos
                    + ", lastPos=" + previousPos;
            if (!player.canSkipTicks()) {
                if (flag(V.write(verbose()).str(face).str(previousFace).str(pos).str(previousPos)) && alert(verbose) && shouldModifyPackets()) {
                    blockBreak.cancel();
                }
            } else {
                flags.add(new FlagData(verbose, face, previousFace, pos, previousPos));
            }
        }

        lastFace = blockBreak.face;
        lastPos = blockBreak.position;
        hasBroken = true;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!player.cameraEntity.isSelf() || isTickPacket(event.getPacketType())) {
            hasBroken = false;
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!player.canSkipTicks()) return;

        if (player.isTickingReliablyFor(3)) {
            for (FlagData data : flags) {
                String verbose = data.verbose();
                if (flag(V.write(verbose()).str(data.face()).str(data.previousFace()).str(data.pos()).str(data.previousPos()))) {
                    alert(verbose);
                }
            }
        }

        flags.clear();
    }

    private record FlagData(String verbose, String face, String previousFace, String pos, String previousPos) {
    }
}
