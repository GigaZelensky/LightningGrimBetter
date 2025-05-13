package ac.grim.grimac.utils.data.packetentity;

import ac.grim.grimac.api.packet.entity.PacketEntityType;
import ac.grim.grimac.api.packet.entity.PacketEntityTypes;
import lombok.Getter;

@Getter
public abstract class TypedPacketEntity {
    private final PacketEntityType type;
    private final boolean isLivingEntity, isMinecart, isHorse, isAgeable, isAnimal, isBoat;

    public TypedPacketEntity(PacketEntityType type) {
        this.type = type;
        this.isLivingEntity = PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.LIVINGENTITY);
        this.isMinecart = PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.MINECART_ABSTRACT);
        this.isHorse = PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.ABSTRACT_HORSE);
        // isAgeable really means "is there a baby version of this mob" and is no longer the term used in modern Minecraft
        this.isAgeable = // armor stands are not included here because it has a separate tag called isSmall, though it does the same thing
                (PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.ABSTRACT_AGEABLE) && !(PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.ABSTRACT_PARROT) || type == PacketEntityTypes.FROG))
                        || PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.ZOMBIE)
                        || PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.ABSTRACT_PIGLIN)
                        || type == PacketEntityTypes.ZOGLIN;
        this.isAnimal = PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.ABSTRACT_ANIMAL);
        this.isBoat = PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.BOAT);
    }

    public boolean isPushable() {
        // Players can only push living entities
        // Minecarts and boats are the only non-living that can push
        // Bats, parrots, and armor stands cannot
        if (type == PacketEntityTypes.ARMOR_STAND || type == PacketEntityTypes.BAT || type == PacketEntityTypes.PARROT)
            return false;
        return isLivingEntity || isBoat || isMinecart;
    }
}
