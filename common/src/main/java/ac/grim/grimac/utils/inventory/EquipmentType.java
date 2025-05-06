package ac.grim.grimac.utils.inventory;

import ac.grim.grimac.api.packet.item.PacketItemStack;
import ac.grim.grimac.api.packet.item.PacketItemType;
import ac.grim.grimac.api.packet.item.PacketItemTypes;

public enum EquipmentType {
    MAINHAND,
    OFFHAND,
    FEET,
    LEGS,
    CHEST,
    HEAD;

    public static EquipmentType byArmorID(int id) {
        return switch (id) {
            case 0 -> HEAD;
            case 1 -> CHEST;
            case 2 -> LEGS;
            case 3 -> FEET;
            default -> MAINHAND;
        };
    }

    // TODO make this data driven and moddable
    public static EquipmentType getEquipmentSlotForItem(PacketItemStack itemStack) {
        PacketItemType item = itemStack.getType();
        if (item == PacketItemTypes.CARVED_PUMPKIN || (item.getName().getKey().contains("SKULL") ||
                (item.getName().getKey().contains("HEAD") && !item.getName().getKey().contains("PISTON")))) {
            return HEAD;
        }
        if (item == PacketItemTypes.ELYTRA) {
            return CHEST;
        }
        if (item == PacketItemTypes.LEATHER_BOOTS || item == PacketItemTypes.CHAINMAIL_BOOTS
                || item == PacketItemTypes.IRON_BOOTS || item == PacketItemTypes.DIAMOND_BOOTS
                || item == PacketItemTypes.GOLDEN_BOOTS || item == PacketItemTypes.NETHERITE_BOOTS) {
            return FEET;
        }
        if (item == PacketItemTypes.LEATHER_LEGGINGS || item == PacketItemTypes.CHAINMAIL_LEGGINGS
                || item == PacketItemTypes.IRON_LEGGINGS || item == PacketItemTypes.DIAMOND_LEGGINGS
                || item == PacketItemTypes.GOLDEN_LEGGINGS || item == PacketItemTypes.NETHERITE_LEGGINGS) {
            return LEGS;
        }
        if (item == PacketItemTypes.LEATHER_CHESTPLATE || item == PacketItemTypes.CHAINMAIL_CHESTPLATE
                || item == PacketItemTypes.IRON_CHESTPLATE || item == PacketItemTypes.DIAMOND_CHESTPLATE
                || item == PacketItemTypes.GOLDEN_CHESTPLATE || item == PacketItemTypes.NETHERITE_CHESTPLATE) {
            return CHEST;
        }
        if (item == PacketItemTypes.LEATHER_HELMET || item == PacketItemTypes.CHAINMAIL_HELMET
                || item == PacketItemTypes.IRON_HELMET || item == PacketItemTypes.DIAMOND_HELMET
                || item == PacketItemTypes.GOLDEN_HELMET || item == PacketItemTypes.NETHERITE_HELMET) {
            return HEAD;
        }
        return PacketItemTypes.SHIELD == item ? OFFHAND : MAINHAND;
    }

    public boolean isArmor() {
        return this == FEET || this == LEGS || this == CHEST || this == HEAD;
    }

    public int getIndex() {
        return switch (this) {
            case MAINHAND, FEET -> 0;
            case OFFHAND, LEGS -> 1;
            case CHEST -> 2;
            case HEAD -> 3;
        };
    }
}
