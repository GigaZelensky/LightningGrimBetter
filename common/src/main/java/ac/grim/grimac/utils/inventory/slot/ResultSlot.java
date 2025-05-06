package ac.grim.grimac.utils.inventory.slot;

import ac.grim.grimac.api.packet.item.PacketItemStack;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.inventory.InventoryStorage;

public class ResultSlot extends Slot {

    public ResultSlot(InventoryStorage container, int slot) {
        super(container, slot);
    }

    @Override
    public boolean mayPlace(PacketItemStack itemStack) {
        return false;
    }

    @Override
    public void onTake(GrimPlayer player, PacketItemStack itemStack) {
        // Resync the player's inventory
    }
}
