package ac.grim.grimac.utils.data.packetentity;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.entity.PacketEntityType;
import com.github.retrooper.packetevents.protocol.world.BlockFace;

import java.util.UUID;

public class PacketEntityShulker extends PacketEntity {
    public BlockFace facing = BlockFace.DOWN;

    public PacketEntityShulker(GrimPlayer player, UUID uuid, PacketEntityType type, double x, double y, double z) {
        super(player, uuid, type, x, y, z);
    }
}
