package ac.grim.boar.anticheat.packets.other;

import ac.grim.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.grim.boar.anticheat.player.BoarPlayer;
import ac.grim.boar.anticheat.player.data.VehicleData;
import ac.grim.boar.protocol.event.CloudburstPacketEvent;
import ac.grim.boar.protocol.listener.PacketListener;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket;

public class VehiclePackets implements PacketListener {
    @Override
    public void onPacketSend(CloudburstPacketEvent event, boolean immediate) {
        final BoarPlayer player = event.getPlayer();
        if (event.getPacket() instanceof SetEntityLinkPacket packet) {
            final EntityLinkData link = packet.getEntityLink();
            if (link == null) {
                return;
            }

            long entityId = packet.getEntityLink().getFrom();
            long riderId = packet.getEntityLink().getTo();

            // We don't have to handle this.
            if (riderId != player.runtimeEntityId) {
                return;
            }

            final EntityCache cache = player.compensatedWorld.getEntity(entityId);
            if (cache == null) {
                // Likely won't happen, but why not!
                return;
            }

            // Yep.
            player.getTeleportUtil().getQueuedTeleports().clear();

            player.sendLatencyStack(immediate);
            if (link.getType() == EntityLinkData.Type.REMOVE) {
                player.latencyUtil.addTaskToQueue(player.sentStackId.get(), () -> player.vehicleData = null);
                return;
            }

            player.latencyUtil.addTaskToQueue(player.sentStackId.get(), () -> {
                player.vehicleData = new VehicleData();
                player.vehicleData.canWeControlThisVehicle = link.getType() == EntityLinkData.Type.RIDER;
                player.vehicleData.vehicleRuntimeId = entityId;
            });
        }
    }
}
