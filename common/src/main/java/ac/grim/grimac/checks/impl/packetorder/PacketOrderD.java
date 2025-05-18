package ac.grim.grimac.checks.impl.packetorder;

import ac.grim.grimac.api.packet.player.enums.InteractionHand;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.client.play.ClientInteractEntityPacket;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;

@CheckData(name = "PacketOrderD", experimental = true)
public class PacketOrderD extends Check implements PacketCheck {
    public PacketOrderD(final GrimPlayer player) {
        super(player);
    }

    private boolean sentMainhand;
    private int requiredEntity;
    private boolean requiredSneaking;

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.INTERACT_ENTITY && player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9)) {
            final ClientInteractEntityPacket packet = packetFactory.clientInteractEntity(event);
            ClientInteractEntityPacket.InteractAction action = packet.getInteractAction();
            if (action != ClientInteractEntityPacket.InteractAction.ATTACK) {
                final boolean sneaking = packet.isSneaking().orElse(false);
                final int entity = packet.getEntityId();

                if (packet.getInteractionHand() == InteractionHand.OFF_HAND) {
                    if (action == ClientInteractEntityPacket.InteractAction.INTERACT) {
                        if (!sentMainhand) {
                            if (flagAndAlert("Skipped Mainhand") && shouldModifyPackets()) {
                                event.setCancelled(true);
                                player.onPacketCancel();
                            }
                        }
                        sentMainhand = false;
                    } else if (sneaking != requiredSneaking || entity != requiredEntity) {
                        String verbose = "requiredEntity=" + requiredEntity + ", entity=" + entity
                                + ", requiredSneaking=" + requiredSneaking + ", sneaking=" + sneaking;
                        if (flagAndAlert(verbose) && shouldModifyPackets()) {
                            event.setCancelled(true);
                            player.onPacketCancel();
                        }
                    }
                } else {
                    requiredEntity = entity;
                    requiredSneaking = sneaking;
                    sentMainhand = true;
                }
            }
        }

        if (isTickPacket(event.getPacketType())) {
            sentMainhand = false;
        }
    }
}
