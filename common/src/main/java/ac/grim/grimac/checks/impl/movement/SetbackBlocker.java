package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;

public class SetbackBlocker extends Check implements PacketCheck {
    public SetbackBlocker(GrimPlayer playerData) {
        super(playerData);
    }

    public void onPacketReceive(final PacketReceiveEvent event) {
        if (player.disableGrim)
            return; // Let's avoid letting people disable grim with grim.nomodifypackets

        if (event.getPacketType() == PacketTypes.Play.Client.INTERACT_ENTITY) {
            if (player.getSetbackTeleportUtil().cheatVehicleInterpolationDelay > 0) {
                event.setCancelled(true); // Player is in the vehicle
            }
        }

        // Don't block teleport packets
        if (player.packetStateData.lastPacketWasTeleport) return;

        if (isFlying(event.getPacketType())) {
            // The player must obey setbacks
            if (player.getSetbackTeleportUtil().shouldBlockMovement()) {
                event.setCancelled(true);
            }

            // Look is the only valid packet to send while in a vehicle
            if (player.inVehicle() && event.getPacketType() != PacketTypes.Play.Client.PLAYER_ROTATION && !player.packetStateData.lastPacketWasTeleport) {
                event.setCancelled(true);
            }

            // The player is sleeping, should be safe to block position packets
            if (player.isInBed && MCPacket.getAPI().getVectorFactory().getImmutableVec3d(player.x, player.y, player.z).distanceSquared(player.bedPosition) > 1) {
                event.setCancelled(true);
            }

            // Player is dead
            if (player.compensatedEntities.self.isDead) {
                event.setCancelled(true);
            }
        }

        if (event.getPacketType() == PacketTypes.Play.Client.VEHICLE_MOVE) {
            if (player.getSetbackTeleportUtil().shouldBlockMovement()) {
                event.setCancelled(true);
            }

            // Don't let a player move a vehicle when not in a vehicle
            if (!player.inVehicle()) {
                event.setCancelled(true);
            }

            // A player is sleeping while in a vehicle
            if (player.isInBed) {
                event.setCancelled(true);
            }

            // Player is dead
            if (player.compensatedEntities.self.isDead) {
                event.setCancelled(true);
            }
        }
    }
}
