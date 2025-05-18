package ac.grim.grimac.checks.impl.multiactions;

import ac.grim.grimac.api.packet.player.enums.DiggingAction;
import ac.grim.grimac.api.packet.player.enums.InteractionHand;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.client.play.ClientPlayerDiggingPacket;

@CheckData(name = "MultiActionsB", description = "Breaking blocks while using an item", experimental = true)
public class MultiActionsB extends Check implements PacketCheck {
    public MultiActionsB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.packetStateData.isSlowedByUsingItem() && (player.packetStateData.lastSlotSelected == player.packetStateData.getSlowedByUsingItemSlot() || player.packetStateData.eatingHand == InteractionHand.OFF_HAND) && event.getPacketType() == PacketTypes.Play.Client.PLAYER_DIGGING) {
            // this is vanilla on 1.7
            if (player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_7_10)) {
                return;
            }

            final ClientPlayerDiggingPacket packet = packetFactory.clientPlayerDigging(event);

            if (packet.getDiggingAction() == DiggingAction.START_DIGGING || packet.getDiggingAction() == DiggingAction.CANCELLED_DIGGING || packet.getDiggingAction() == DiggingAction.FINISHED_DIGGING) {
                if (flagAndAlert() && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                    player.resyncPosition(packet.getBlockPosition());
                }
            }
        }
    }
}
