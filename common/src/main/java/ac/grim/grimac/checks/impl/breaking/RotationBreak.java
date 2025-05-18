package ac.grim.grimac.checks.impl.breaking;

import ac.grim.grimac.api.packet.MCPacket;
import ac.grim.grimac.api.packet.player.enums.DiggingAction;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3f;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.BlockBreakCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockBreak;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.Pair;
import ac.grim.grimac.api.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.Ray;
import ac.grim.grimac.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import ac.grim.grimac.api.packet.world.enums.BlockFace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CheckData(name = "RotationBreak", experimental = true)
public class RotationBreak extends Check implements BlockBreakCheck {
    private double flagBuffer = 0; // If the player flags once, force them to play legit, or we will cancel the tick before.
    private boolean ignorePost = false;

    public RotationBreak(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockBreak(BlockBreak blockBreak) {
        if (player.gamemode == GameMode.SPECTATOR)
            return; // you don't send flying packets when spectating entities
        if (player.inVehicle()) return; // falses
        if (blockBreak.action == DiggingAction.CANCELLED_DIGGING) return; // falses

        if (flagBuffer > 0 && !didRayTraceHit(blockBreak)) {
            ignorePost = true;
            // If the player hit and has flagged this check recently
            if (flagAndAlert("pre-flying, action=" + blockBreak.action) && shouldModifyPackets()) {
                blockBreak.cancel();
            }
        }
    }

    @Override
    public void onPostFlyingBlockBreak(BlockBreak blockBreak) {
        if (player.gamemode == GameMode.SPECTATOR)
            return; // you don't send flying packets when spectating entities
        if (player.inVehicle()) return; // falses
        if (blockBreak.action == DiggingAction.CANCELLED_DIGGING) return; // falses

        // Don't flag twice
        if (ignorePost) {
            ignorePost = false;
            return;
        }

        if (didRayTraceHit(blockBreak)) {
            flagBuffer = Math.max(0, flagBuffer - 0.1);
        } else {
            flagBuffer = 1;
            flagAndAlert("post-flying, action=" + blockBreak.action);
        }
    }

    private boolean didRayTraceHit(BlockBreak blockBreak) {
        SimpleCollisionBox box = new SimpleCollisionBox(blockBreak.position);

        final double[] possibleEyeHeights = player.getPossibleEyeHeights();

        // Start checking if player is in the block
        double minEyeHeight = Double.MAX_VALUE;
        double maxEyeHeight = Double.MIN_VALUE;
        for (double height : possibleEyeHeights) {
            minEyeHeight = Math.min(minEyeHeight, height);
            maxEyeHeight = Math.max(maxEyeHeight, height);
        }

        SimpleCollisionBox eyePositions = new SimpleCollisionBox(player.x, player.y + minEyeHeight, player.z, player.x, player.y + maxEyeHeight, player.z);
        eyePositions.expand(player.getMovementThreshold());

        // If the player is inside a block, then they can ray trace through the block and hit the other side of the block
        if (eyePositions.isIntersected(box)) {
            return true;
        }
        // End checking if the player is in the block

        List<ImmutableVector3f> possibleLookDirs = new ArrayList<>(Arrays.asList(
                MCPacket.getAPI().getVectorFactory().getImmutableVector3f(player.lastXRot, player.yRot, 0),
                MCPacket.getAPI().getVectorFactory().getImmutableVector3f(player.xRot, player.yRot, 0)
        ));

        // 1.9+ players could be a tick behind because we don't get skipped ticks
        if (player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9)) {
            possibleLookDirs.add(MCPacket.getAPI().getVectorFactory().getImmutableVector3f(player.lastXRot, player.lastYRot, 0));
        }

        // 1.7 players do not have any of these issues! They are always on the latest look vector
        if (player.getClientVersion().isOlderThan(PacketClientVersions.V_1_8)) {
            possibleLookDirs = Collections.singletonList(MCPacket.getAPI().getVectorFactory().getImmutableVector3f(player.xRot, player.yRot, 0));
        }

        final double distance = player.compensatedEntities.self.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        for (double d : possibleEyeHeights) {
            for (ImmutableVector3f lookDir : possibleLookDirs) {
                ImmutableVector3d starting = MCPacket.getAPI().getVectorFactory().getImmutableVec3d(player.x, player.y + d, player.z);
                Ray trace = new Ray(player, starting.getX(), starting.getY(), starting.getZ(), lookDir.getX(), lookDir.getY());
                Pair<Vector3dm, BlockFace> intercept = ReachUtils.calculateIntercept(box, trace.getOrigin(), trace.getPointAtDistance(distance));

                if (intercept.first() != null) return true;
            }
        }

        return false;
    }
}
