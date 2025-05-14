package ac.grim.grimac.utils.collisions.blocks.connecting;

import ac.grim.grimac.api.packet.block.PacketBlockState;
import ac.grim.grimac.api.packet.item.PacketStateType;
import ac.grim.grimac.api.packet.protocol.PacketClientVersion;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.world.PacketStateTypes;
import ac.grim.grimac.api.packet.world.enums.East;
import ac.grim.grimac.api.packet.world.enums.South;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.CollisionData;
import ac.grim.grimac.utils.collisions.datatypes.CollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.CollisionFactory;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import ac.grim.grimac.api.packet.world.enums.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import ac.grim.grimac.api.packet.world.enums.North;
import ac.grim.grimac.api.packet.world.enums.West;

public class DynamicCollisionFence extends DynamicConnecting implements CollisionFactory {
    private static final CollisionBox[] COLLISION_BOXES = makeShapes(2.0F, 2.0F, 24.0F, 0.0F, 24.0F, true, 1);

    @Override
    public CollisionBox fetch(GrimPlayer player, PacketClientVersion version, PacketBlockState block, int x, int y, int z) {
        boolean east;
        boolean north;
        boolean south;
        boolean west;

        // 1.13+ servers on 1.13+ clients send the full fence data
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)
                && version.isNewerThanOrEquals(PacketClientVersions.V_1_13)) {
            east = block.getEast() != East.FALSE;
            north = block.getNorth() != North.FALSE;
            south = block.getSouth() != South.FALSE;
            west = block.getWest() != West.FALSE;
        } else {
            east = connectsTo(player, version, x, y, z, BlockFace.EAST);
            north = connectsTo(player, version, x, y, z, BlockFace.NORTH);
            south = connectsTo(player, version, x, y, z, BlockFace.SOUTH);
            west = connectsTo(player, version, x, y, z, BlockFace.WEST);
        }

        return COLLISION_BOXES[getAABBIndex(north, east, south, west)].copy();
    }

    @Override
    public boolean checkCanConnect(GrimPlayer player, PacketBlockState state, PacketStateType one, PacketStateType two, BlockFace direction) {
        if (BlockTags.FENCES.contains(one))
            return !(one == PacketStateTypes.NETHER_BRICK_FENCE) && !(two == PacketStateTypes.NETHER_BRICK_FENCE);
        else
            return BlockTags.FENCES.contains(one) || CollisionData.getData(one).getMovementCollisionBox(player, player.getClientVersion(), state, 0, 0, 0).isSideFullBlock(direction);
    }
}
