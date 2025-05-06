package ac.grim.grimac.utils.inventory;

import ac.grim.grimac.api.packet.item.PacketEnchantmentType;
import ac.grim.grimac.utils.latency.CompensatedInventory;
import ac.grim.grimac.api.packet.item.PacketItemStack;

public class EnchantmentHelper {
    // Some enchants work on any armor piece but only the maximum level counts
    public static int getMaximumEnchantLevel(CompensatedInventory inventory, PacketEnchantmentType enchantmentType, int protocolVersion) {
        int maxEnchantLevel = 0;

        PacketItemStack helmet = inventory.getHelmet();
        if (helmet != PacketItemStack.EMPTY) {
            maxEnchantLevel = Math.max(maxEnchantLevel, helmet.getEnchantmentLevel(enchantmentType, protocolVersion));
        }

        PacketItemStack chestplate = inventory.getChestplate();
        if (chestplate != PacketItemStack.EMPTY) {
            maxEnchantLevel = Math.max(maxEnchantLevel, chestplate.getEnchantmentLevel(enchantmentType, protocolVersion));
        }

        PacketItemStack leggings = inventory.getLeggings();
        if (leggings != PacketItemStack.EMPTY) {
            maxEnchantLevel = Math.max(maxEnchantLevel, leggings.getEnchantmentLevel(enchantmentType, protocolVersion));
        }

        PacketItemStack boots = inventory.getBoots();
        if (boots != PacketItemStack.EMPTY) {
            maxEnchantLevel = Math.max(maxEnchantLevel, boots.getEnchantmentLevel(enchantmentType, protocolVersion));
        }

        return maxEnchantLevel;
    }
}
