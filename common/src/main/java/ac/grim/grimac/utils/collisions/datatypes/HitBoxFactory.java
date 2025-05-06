package ac.grim.grimac.utils.collisions.datatypes;

import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import ac.grim.grimac.api.packet.item.PacketStateType;

public interface HitBoxFactory {
    CollisionBox fetch(GrimPlayer player, PacketStateType heldItem, ClientVersion version, WrappedBlockState block, boolean isTargetBlock, int x, int y, int z);
}
