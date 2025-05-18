package ac.grim.grimac.checks.impl.badpackets;

import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;

@CheckData(name = "BadPacketsY", description = "Sent out of bounds slot id")
public class BadPacketsY extends Check implements PacketCheck {
    public BadPacketsY(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketTypes.Play.Client.HELD_ITEM_CHANGE) {
            final int slot = packetFactory.clientHeldItemChange(event).getSlot();
            if (slot > 8 || slot < 0) { // ban
                if (flagAndAlert("slot=" + slot) && shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
            }
        }
    }
}
