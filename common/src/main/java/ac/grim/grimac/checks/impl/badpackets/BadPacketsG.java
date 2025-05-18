package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.types.client.play.ClientEntityActionPacket;

@CheckData(name = "BadPacketsG", description = "Sent duplicate sneaking status")
public class BadPacketsG extends Check implements PacketCheck {
    private boolean lastSneaking, respawn;

    public BadPacketsG(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.ENTITY_ACTION) {
            ClientEntityActionPacket packet = packetFactory.clientEntityAction(event);

            if (packet.getAction() == ClientEntityActionPacket.Action.START_SNEAKING) {
                // The player may send two START_SNEAKING packets if they respawned
                if (lastSneaking && !respawn) {
                    if (flagAndAlert("state=true") && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                } else {
                    lastSneaking = true;
                }
                respawn = false;
            } else if (packet.getAction() == ClientEntityActionPacket.Action.STOP_SNEAKING) {
                if (!lastSneaking && !respawn) {
                    if (flagAndAlert("state=false") && shouldModifyPackets()) {
                        event.setCancelled(true);
                        player.onPacketCancel();
                    }
                } else {
                    lastSneaking = false;
                }
                respawn = false;
            }
        }
    }

    public void handleRespawn() {
        // Clients could potentially not send a STOP_SNEAKING packet when they die, so we need to track it
        respawn = true;
    }
}
