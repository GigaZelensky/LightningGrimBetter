package ac.grim.grimac.utils.data.packetentity;

import ac.grim.grimac.api.packet.entity.PacketEntityType;
import ac.grim.grimac.player.GrimPlayer;

import java.util.UUID;

public class PacketEntityHook extends PacketEntityUnHittable {
    public int owner;
    public int attached = -1;

    public PacketEntityHook(GrimPlayer player, UUID uuid, PacketEntityType type, double x, double y, double z, int owner) {
        super(player, uuid, type, x, y, z);
        this.owner = owner;
    }
}
