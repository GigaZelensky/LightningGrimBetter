package ac.grim.grimac.utils.inventory.inventory;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.inventory.Inventory;
import ac.grim.grimac.utils.inventory.InventoryStorage;
import ac.grim.grimac.utils.inventory.slot.Slot;
import ac.grim.grimac.api.packet.item.PacketItemStack;

public class BasicInventoryMenu extends AbstractContainerMenu {
    int rows;

    public BasicInventoryMenu(GrimPlayer player, Inventory playerInventory, int rows) {
        super(player, playerInventory);
        this.rows = rows;

        InventoryStorage containerStorage = new InventoryStorage(rows * 9);

        for (int i = 0; i < rows * 9; i++) {
            addSlot(new Slot(containerStorage, i));
        }

        addFourRowPlayerInventory();
    }

    @Override
    public PacketItemStack quickMoveStack(int slotID) {
        PacketItemStack itemstack = PacketItemStack.EMPTY;
        Slot slot = this.slots.get(slotID);
        if (slot != null && slot.hasItem()) {
            PacketItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (slotID < this.rows * 9) {
                if (!this.moveItemStackTo(itemstack1, this.rows * 9, this.slots.size(), true)) {
                    return PacketItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, this.rows * 9, false)) {
                return PacketItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(PacketItemStack.EMPTY);
            }
        }

        return itemstack;
    }
}
