package ac.grim.grimac.utils.inventory;

import ac.grim.grimac.api.packet.item.PacketItemStack;
import lombok.Getter;

public class InventoryStorage {
    protected PacketItemStack[] items;
    @Getter
    int size;

    public InventoryStorage(int size) {
        this.items = new PacketItemStack[size];
        this.size = size;

        for (int i = 0; i < size; i++) {
            items[i] = PacketItemStack.EMPTY;
        }
    }

    public void setItem(int item, PacketItemStack stack) {
        items[item] = stack == null ? PacketItemStack.EMPTY : stack;
    }

    public PacketItemStack getItem(int index) {
        return items[index];
    }

    public PacketItemStack removeItem(int slot, int amount) {
        return slot >= 0 && slot < items.length && !items[slot].isEmpty() && amount > 0 ? items[slot].split(amount) : PacketItemStack.EMPTY;
    }

    public int getMaxStackSize() {
        return 64;
    }
}
