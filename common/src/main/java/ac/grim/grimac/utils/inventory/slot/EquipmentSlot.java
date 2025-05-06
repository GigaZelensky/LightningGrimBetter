package ac.grim.grimac.utils.inventory.slot;

import ac.grim.grimac.api.packet.item.PacketEnchantmentTypes;
import ac.grim.grimac.api.packet.item.PacketItemStack;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.inventory.EquipmentType;
import ac.grim.grimac.utils.inventory.InventoryStorage;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;

public class EquipmentSlot extends Slot {
    EquipmentType type;

    public EquipmentSlot(EquipmentType type, InventoryStorage menu, int slot) {
        super(menu, slot);
        this.type = type;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean mayPlace(PacketItemStack itemStack) {
        return type == EquipmentType.getEquipmentSlotForItem(itemStack);
    }

    public boolean mayPickup(GrimPlayer player) {
        PacketItemStack itemstack = this.getItem();
        return (itemstack.isEmpty() || player.gamemode == GameMode.CREATIVE || itemstack.getEnchantmentLevel(PacketEnchantmentTypes.BINDING_CURSE, PacketEvents.getAPI().getServerManager().getVersion().toClientVersion().getProtocolVersion()) == 0) && super.mayPickup(player);
    }
}
