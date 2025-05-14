package ac.grim.grimac.utils.collisions.blocks;

import ac.grim.grimac.api.packet.item.PacketStateType;
import ac.grim.grimac.api.packet.protocol.PacketClientVersion;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.world.PacketStateTypes;
import ac.grim.grimac.api.packet.world.enums.South;
import ac.grim.grimac.api.packet.world.enums.West;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.CollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.CollisionFactory;
import ac.grim.grimac.utils.collisions.datatypes.ComplexCollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import ac.grim.grimac.api.packet.world.enums.BlockFace;
import ac.grim.grimac.api.packet.block.PacketBlockState;
import ac.grim.grimac.api.packet.world.enums.East;
import ac.grim.grimac.api.packet.world.enums.North;

import java.util.HashSet;
import java.util.Set;

// 1.13 clients on 1.12 servers have this mostly working, but chorus flowers don’t attach to chorus plants.
// 1.12 clients run their usual calculations on 1.13 servers, same as 1.12 servers
// 1.13 clients on 1.13 servers get everything included in the block data, no world reading required
public class DynamicChorusPlant implements CollisionFactory {
    private static final BlockFace[] directions = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
    private static final CollisionBox[] modernShapes = makeShapes();

    private static CollisionBox[] makeShapes() {
        float f = 0.5F - (float) 0.3125;
        float f1 = 0.5F + (float) 0.3125;
        SimpleCollisionBox baseShape = new SimpleCollisionBox(f, f, f, f1, f1, f1, false);
        SimpleCollisionBox[] avoxelshape = new SimpleCollisionBox[directions.length];

        for (int i = 0; i < directions.length; ++i) {
            BlockFace direction = directions[i];
            avoxelshape[i] = new SimpleCollisionBox(0.5D + Math.min(-(float) 0.3125, (double) direction.getModX() * 0.5D), 0.5D + Math.min(-(float) 0.3125, (double) direction.getModY() * 0.5D), 0.5D + Math.min(-(float) 0.3125, (double) direction.getModZ() * 0.5D), 0.5D + Math.max((float) 0.3125, (double) direction.getModX() * 0.5D), 0.5D + Math.max((float) 0.3125, (double) direction.getModY() * 0.5D), 0.5D + Math.max((float) 0.3125, (double) direction.getModZ() * 0.5D), false);
        }

        CollisionBox[] avoxelshape1 = new CollisionBox[64];

        for (int k = 0; k < 64; ++k) {
            ComplexCollisionBox directionalShape = new ComplexCollisionBox(7, baseShape); // how big is this one??

            for (int j = 0; j < directions.length; ++j) {
                if ((k & 1 << j) != 0) {
                    directionalShape.add(avoxelshape[j]);
                }
            }

            avoxelshape1[k] = directionalShape;
        }

        return avoxelshape1;
    }

    @Override
    public CollisionBox fetch(GrimPlayer player, PacketClientVersion version, PacketBlockState block, int x, int y, int z) {
        // ViaVersion replacement block (Purple wool)
        if (version.isOlderThanOrEquals(PacketClientVersions.V_1_8))
            return new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true);

        // Player is 1.12- on 1.13 server
        // Player is 1.12 on 1.12 server
        if (version.isOlderThanOrEquals(PacketClientVersions.V_1_12_2)) {
            return getLegacyBoundingBox(player, version, x, y, z);
        }

        Set<BlockFace> directions;

        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
            // Player is 1.13 on 1.13 server
            directions = new HashSet<>();
            if (block.west() == West.TRUE) directions.add(BlockFace.WEST);
            if (block.east() == East.TRUE) directions.add(BlockFace.EAST);
            if (block.north() == North.TRUE) directions.add(BlockFace.NORTH);
            if (block.south() == South.TRUE) directions.add(BlockFace.SOUTH);
            if (block.isUp()) directions.add(BlockFace.UP);
            if (block.isDown()) directions.add(BlockFace.DOWN);
        } else {
            // Player is 1.13 on 1.12 server
            directions = getLegacyStates(player, version, x, y, z);
        }

        // Player is 1.13+ on 1.13+ server
        return modernShapes[getAABBIndex(directions)].copy();
    }

    public CollisionBox getLegacyBoundingBox(GrimPlayer player, PacketClientVersion version, int x, int y, int z) {
        Set<BlockFace> faces = getLegacyStates(player, version, x, y, z);

        float f1 = faces.contains(BlockFace.WEST) ? 0.0F : 0.1875F;
        float f2 = faces.contains(BlockFace.DOWN) ? 0.0F : 0.1875F;
        float f3 = faces.contains(BlockFace.NORTH) ? 0.0F : 0.1875F;
        float f4 = faces.contains(BlockFace.EAST) ? 1.0F : 0.8125F;
        float f5 = faces.contains(BlockFace.UP) ? 1.0F : 0.8125F;
        float f6 = faces.contains(BlockFace.SOUTH) ? 1.0F : 0.8125F;

        return new SimpleCollisionBox(f1, f2, f3, f4, f5, f6);
    }

    public Set<BlockFace> getLegacyStates(GrimPlayer player, PacketClientVersion version, int x, int y, int z) {
        Set<BlockFace> faces = new HashSet<>();

        // 1.13 clients on 1.12 servers don't see chorus flowers attached to chorus because of a ViaVersion bug
        PacketStateType versionFlower = version.isOlderThanOrEquals(PacketClientVersions.V_1_12_2) ? PacketStateTypes.CHORUS_FLOWER : null;

        PacketStateType downBlock = player.compensatedWorld.getBlockType(x, y - 1, z);
        PacketStateType upBlock = player.compensatedWorld.getBlockType(x, y + 1, z);
        PacketStateType northBlock = player.compensatedWorld.getBlockType(x, y, z - 1);
        PacketStateType eastBlock = player.compensatedWorld.getBlockType(x + 1, y, z);
        PacketStateType southBlock = player.compensatedWorld.getBlockType(x, y, z + 1);
        PacketStateType westBlock = player.compensatedWorld.getBlockType(x - 1, y, z);

        if (downBlock == PacketStateTypes.CHORUS_PLANT || downBlock == versionFlower || downBlock == PacketStateTypes.END_STONE) {
            faces.add(BlockFace.DOWN);
        }

        if (upBlock == PacketStateTypes.CHORUS_PLANT || upBlock == versionFlower) {
            faces.add(BlockFace.UP);
        }
        if (northBlock == PacketStateTypes.CHORUS_PLANT || northBlock == versionFlower) {
            faces.add(BlockFace.EAST);
        }
        if (eastBlock == PacketStateTypes.CHORUS_PLANT || eastBlock == versionFlower) {
            faces.add(BlockFace.EAST);
        }
        if (southBlock == PacketStateTypes.CHORUS_PLANT || southBlock == versionFlower) {
            faces.add(BlockFace.NORTH);
        }
        if (westBlock == PacketStateTypes.CHORUS_PLANT || westBlock == versionFlower) {
            faces.add(BlockFace.NORTH);
        }

        return faces;
    }

    protected int getAABBIndex(Set<BlockFace> p_196486_1_) {
        int i = 0;

        for (int j = 0; j < directions.length; ++j) {
            if (p_196486_1_.contains(directions[j])) {
                i |= 1 << j;
            }
        }

        return i;
    }
}
