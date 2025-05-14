package ac.grim.grimac.utils.collisions.datatypes;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.protocol.PacketClientVersion;
import ac.grim.grimac.api.packet.block.PacketBlockState;
import ac.grim.grimac.api.packet.item.PacketStateType;

public interface HitBoxFactory {
    CollisionBox fetch(GrimPlayer player, PacketStateType heldItem, PacketClientVersion version, PacketBlockState block, boolean isTargetBlock, int x, int y, int z);
}
