package ac.grim.grimac.checks.impl.multiactions;

import ac.grim.grimac.api.packet.player.enums.InteractionHand;
import ac.grim.grimac.api.packet.types.client.play.ClientInteractEntityPacket;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.api.packet.types.PacketTypes;

@CheckData(name = "MultiActionsA", description = "Attacked while using an item", experimental = true)
public class MultiActionsA extends Check implements PacketCheck {
    public MultiActionsA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (player.packetStateData.isSlowedByUsingItem() && (player.packetStateData.lastSlotSelected == player.packetStateData.getSlowedByUsingItemSlot() || player.packetStateData.eatingHand == InteractionHand.OFF_HAND) && event.getPacketType() == PacketTypes.Play.Client.INTERACT_ENTITY) {
            if (packetFactory.clientInteractEntity(event).getInteractAction() == ClientInteractEntityPacket.InteractAction.ATTACK) {
                if (flagAndAlert() && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }
    }
}
