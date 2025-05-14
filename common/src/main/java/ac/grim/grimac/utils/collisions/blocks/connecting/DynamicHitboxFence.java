package ac.grim.grimac.utils.collisions.blocks.connecting;

import ac.grim.grimac.api.packet.item.PacketStateType;
import ac.grim.grimac.api.packet.world.PacketStateTypes;
import ac.grim.grimac.api.packet.world.enums.East;
import ac.grim.grimac.api.packet.world.enums.South;
import ac.grim.grimac.api.packet.world.enums.West;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.CollisionData;
import ac.grim.grimac.utils.collisions.datatypes.CollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.ComplexCollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.HitBoxFactory;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import ac.grim.grimac.api.packet.protocol.PacketClientVersion;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.world.enums.BlockFace;
import ac.grim.grimac.api.packet.block.PacketBlockState;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import ac.grim.grimac.api.packet.world.enums.North;

public class DynamicHitboxFence extends DynamicConnecting implements HitBoxFactory {
    private static final CollisionBox[] MODERN_HITBOXES = makeShapes(2.0F, 2.0F, 24.0F, 0.0F, 24.0F, true, 1);
    // no ComplexCollisionBox produced by makeShapes is every larger than 5 SimpleCollisionBoxes
    private static final int MAX_MODERN_HITBOX_COMPLEX_COLLISION_BOX_SIZE = 5;
    public static SimpleCollisionBox[] LEGACY_HITBOXES = new SimpleCollisionBox[]{new SimpleCollisionBox(0.375D, 0.0D, 0.375D, 0.625D, 1.0D, 0.625D), new SimpleCollisionBox(0.375D, 0.0D, 0.375D, 0.625D, 1.0D, 1.0D), new SimpleCollisionBox(0.0D, 0.0D, 0.375D, 0.625D, 1.0D, 0.625D), new SimpleCollisionBox(0.0D, 0.0D, 0.375D, 0.625D, 1.0D, 1.0D), new SimpleCollisionBox(0.375D, 0.0D, 0.0D, 0.625D, 1.0D, 0.625D), new SimpleCollisionBox(0.375D, 0.0D, 0.0D, 0.625D, 1.0D, 1.0D), new SimpleCollisionBox(0.0D, 0.0D, 0.0D, 0.625D, 1.0D, 0.625D), new SimpleCollisionBox(0.0D, 0.0D, 0.0D, 0.625D, 1.0D, 1.0D), new SimpleCollisionBox(0.375D, 0.0D, 0.375D, 1.0D, 1.0D, 0.625D), new SimpleCollisionBox(0.375D, 0.0D, 0.375D, 1.0D, 1.0D, 1.0D), new SimpleCollisionBox(0.0D, 0.0D, 0.375D, 1.0D, 1.0D, 0.625D), new SimpleCollisionBox(0.0D, 0.0D, 0.375D, 1.0D, 1.0D, 1.0D), new SimpleCollisionBox(0.375D, 0.0D, 0.0D, 1.0D, 1.0D, 0.625D), new SimpleCollisionBox(0.375D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D), new SimpleCollisionBox(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.625D), new SimpleCollisionBox(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)};

    static {
        SimpleCollisionBox[] boxes = new SimpleCollisionBox[MAX_MODERN_HITBOX_COMPLEX_COLLISION_BOX_SIZE];

        // we start from one because MODERN_HITBOXES[0] is a NoCollisionBox
        for (int i = 1; i < MODERN_HITBOXES.length; i++) {
            CollisionBox collisionBox = MODERN_HITBOXES[i];
            int size = collisionBox.downCast(boxes);

            for (int j = 0; j < size; j++) {
                if (boxes[j].maxY > 1) {
                    boxes[j].maxY = 1;
                }
            }

            MODERN_HITBOXES[i] = size == 1 ? boxes[0] : new ComplexCollisionBox(size, boxes);
        }
    }

    @Override
    public CollisionBox fetch(GrimPlayer player, PacketStateType heldItem, PacketClientVersion version, PacketBlockState block, boolean isTargetBlock, int x, int y, int z) {
        boolean east;
        boolean north;
        boolean south;
        boolean west;

        // 1.13+ servers on 1.13+ clients send the full fence data
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)
                && version.isNewerThanOrEquals(PacketClientVersions.V_1_13)) {
            east = block.east() != East.FALSE;
            north = block.north() != North.FALSE;
            south = block.south() != South.FALSE;
            west = block.west() != West.FALSE;
        } else {
            east = connectsTo(player, version, x, y, z, BlockFace.EAST);
            north = connectsTo(player, version, x, y, z, BlockFace.NORTH);
            south = connectsTo(player, version, x, y, z, BlockFace.SOUTH);
            west = connectsTo(player, version, x, y, z, BlockFace.WEST);
        }

        return version.isNewerThanOrEquals(PacketClientVersions.V_1_12_2)
                ? getModernCollisionBox(north, east, south, west)
                : getLegacyCollisionBox(north, east, south, west);


    }

    private CollisionBox getLegacyCollisionBox(boolean north, boolean east, boolean south, boolean west) {
        return LEGACY_HITBOXES[getAABBIndex(north, east, south, west)].copy();
    }

    private CollisionBox getModernCollisionBox(boolean north, boolean east, boolean south, boolean west) {
        return MODERN_HITBOXES[getAABBIndex(north, east, south, west)].copy();
    }

    @Override
    public boolean checkCanConnect(GrimPlayer player, PacketBlockState state, PacketStateType one, PacketStateType two, BlockFace direction) {
        if (BlockTags.FENCES.contains(one))
            return !(one == PacketStateTypes.NETHER_BRICK_FENCE) && !(two == PacketStateTypes.NETHER_BRICK_FENCE);
        else
            return BlockTags.FENCES.contains(one) || CollisionData.getData(one).getMovementCollisionBox(player, player.getClientVersion(), state, 0, 0, 0).isSideFullBlock(direction);
    }
}
