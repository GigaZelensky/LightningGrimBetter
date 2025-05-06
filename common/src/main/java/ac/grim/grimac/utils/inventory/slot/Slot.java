package ac.grim.grimac.utils.inventory.slot;

import ac.grim.grimac.api.packet.item.PacketItemStack;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.inventory.InventoryStorage;

import java.util.Optional;

public class Slot {
    public final int inventoryStorageSlot;
    public int slotListIndex;
    InventoryStorage container;

    public Slot(InventoryStorage container, int slot) {
        this.container = container;
        this.inventoryStorageSlot = slot;
    }

    public PacketItemStack getItem() {
        return container.getItem(inventoryStorageSlot);
    }

    public boolean hasItem() {
        return !this.getItem().isEmpty();
    }

    public boolean mayPlace(PacketItemStack itemstack) {
        return true;
    }

    public void set(PacketItemStack itemStack) {
        container.setItem(inventoryStorageSlot, itemStack);
    }

    public int getMaxStackSize() {
        return container.getMaxStackSize();
    }

    public int getMaxStackSize(PacketItemStack itemStack) {
        return Math.min(itemStack.getMaxStackSize(), getMaxStackSize());
    }

    // TODO: Implement for anvil and smithing table
    // TODO: Implement curse of binding support
    public boolean mayPickup() {
        return true;
    }

    public PacketItemStack safeTake(int p_150648_, int p_150649_, GrimPlayer p_150650_) {
        Optional<PacketItemStack> optional = this.tryRemove(p_150648_, p_150649_, p_150650_);
        optional.ifPresent((p_150655_) -> this.onTake(p_150650_, p_150655_));
        return optional.orElse(PacketItemStack.EMPTY);
    }

    public Optional<PacketItemStack> tryRemove(int p_150642_, int p_150643_, GrimPlayer player) {
        if (!this.mayPickup(player)) {
            return Optional.empty();
        } else if (!this.allowModification(player) && p_150643_ < this.getItem().getAmount()) {
            return Optional.empty();
        } else {
            p_150642_ = Math.min(p_150642_, p_150643_);
            PacketItemStack itemstack = this.remove(p_150642_);
            if (itemstack.isEmpty()) {
                return Optional.empty();
            } else {
                if (this.getItem().isEmpty()) {
                    this.set(PacketItemStack.EMPTY);
                }

                return Optional.of(itemstack);
            }
        }
    }

    public PacketItemStack safeInsert(PacketItemStack stack, int amount) {
        if (!stack.isEmpty() && this.mayPlace(stack)) {
            PacketItemStack itemstack = this.getItem();
            int i = Math.min(Math.min(amount, stack.getAmount()), this.getMaxStackSize(stack) - itemstack.getAmount());
            if (itemstack.isEmpty()) {
                this.set(stack.split(i));
            } else if (itemstack.isSameItemSameTags(stack)) {
                stack.shrink(i);
                itemstack.grow(i);
                this.set(itemstack);
            }
        }
        return stack;
    }

    public PacketItemStack remove(int p_40227_) {
        return this.container.removeItem(this.inventoryStorageSlot, p_40227_);
    }

    public void onTake(GrimPlayer player, PacketItemStack itemStack) {

    }

    // No override
    public boolean allowModification(GrimPlayer player) {
        return this.mayPickup(player) && this.mayPlace(this.getItem());
    }

    public boolean mayPickup(GrimPlayer player) {
        return true;
    }
}
