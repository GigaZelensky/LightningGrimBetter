package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.ReachUtils;
import ac.grim.grimac.utils.nmsutil.WorldRayTrace;
import ac.grim.grimac.utils.data.Pair;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.GameMode;

import java.util.List;

@CheckData(name = "FailReach", description = "Swung at a reachable entity but did not attack", experimental = true)
public class FailReach extends Check implements PacketCheck {
    private boolean swungWithoutAttack;

    public FailReach(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.gamemode == GameMode.SPECTATOR || player.inVehicle()) return;

        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            if (isEntityReachable()) {
                swungWithoutAttack = true;
            }
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            swungWithoutAttack = false;
        }

        if (isTickPacket(event.getPacketType())) {
            if (swungWithoutAttack) {
                flagAndAlert();
            }
            swungWithoutAttack = false;
        }
    }

    private boolean isEntityReachable() {
        double maxReach = player.compensatedEntities.self.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        final double[] eyes = player.getPossibleEyeHeights();
        final Vector3dm[] looks = player.getPossibleLookVectors(false);

        for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
            if (!entity.isLivingEntity() || entity.isDead || entity.getType() == EntityTypes.BOAT || entity.getType() == EntityTypes.CHEST_BOAT || entity.getType() == EntityTypes.SHULKER)
                continue;

            SimpleCollisionBox box = entity.getPossibleCollisionBoxes();
            if (player.getClientVersion().isOlderThan(ClientVersion.V_1_9)) {
                box.expand(0.1f);
            }
            if (!player.packetStateData.didLastMovementIncludePosition || player.canSkipTicks()) {
                box.expand(player.getMovementThreshold());
            }

            for (Vector3dm look : looks) {
                for (double eye : eyes) {
                    Vector3dm start = new Vector3dm(player.x, player.y + eye, player.z);
                    Vector3dm end = start.clone().add(new Vector3dm(look.getX() * maxReach, look.getY() * maxReach, look.getZ() * maxReach));
                    Pair<Vector3dm, ?> result = ReachUtils.calculateIntercept(box, start, end);
                    Vector3dm intercept = result.first();
                    if (intercept != null && start.distance(intercept) <= maxReach) {
                        // Make sure no blocks are in the way
                        List<Pair<Double, ?>> list = List.of(new Pair<>(look, eye));
                        Pair<Double, ?> blockHit = WorldRayTrace.didRayTraceHit(player, entity, list, start);
                        if (blockHit.second() == null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
