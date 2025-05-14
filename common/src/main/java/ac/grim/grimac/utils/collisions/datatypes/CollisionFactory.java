package ac.grim.grimac.utils.collisions.datatypes;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.protocol.PacketClientVersion;
import ac.grim.grimac.api.packet.block.PacketBlockState;

public interface CollisionFactory {
    CollisionBox fetch(GrimPlayer player, PacketClientVersion version, PacketBlockState block, int x, int y, int z);
}
