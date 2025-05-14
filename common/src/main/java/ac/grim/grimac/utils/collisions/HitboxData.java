package ac.grim.grimac.utils.collisions;

import ac.grim.grimac.api.packet.block.PacketBlockState;
import ac.grim.grimac.api.packet.item.PacketStateType;
import ac.grim.grimac.api.packet.protocol.PacketClientVersion;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.world.PacketStateTypes;
import ac.grim.grimac.api.packet.world.enums.East;
import ac.grim.grimac.api.packet.world.enums.South;
import ac.grim.grimac.api.packet.world.enums.West;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.blocks.connecting.DynamicHitboxFence;
import ac.grim.grimac.utils.collisions.blocks.connecting.DynamicHitboxPane;
import ac.grim.grimac.utils.collisions.blocks.connecting.DynamicHitboxWall;
import ac.grim.grimac.utils.collisions.datatypes.CollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.ComplexCollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.HexCollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.HexOffsetCollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.HitBoxFactory;
import ac.grim.grimac.utils.collisions.datatypes.NoCollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.OffsetCollisionBox;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.nmsutil.Materials;
import ac.grim.grimac.api.packet.world.enums.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import ac.grim.grimac.api.packet.world.enums.Face;
import ac.grim.grimac.api.packet.world.enums.Half;
import ac.grim.grimac.api.packet.world.enums.Leaves;
import ac.grim.grimac.api.packet.world.enums.North;
import ac.grim.grimac.api.packet.world.enums.Tilt;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Expansion to the CollisionData class, which is different than regular ray tracing hitboxes
public enum HitboxData implements HitBoxFactory {

    RAILS((player, item, version, data, isTargetBlock, x, y, z) -> switch (data.shape()) {
        case ASCENDING_NORTH, ASCENDING_SOUTH, ASCENDING_EAST, ASCENDING_WEST -> {
            if (version.isOlderThan(PacketClientVersions.V_1_8)) {
                PacketStateType railType = data.getType();
                // Activator rails always appear as flat detector rails in 1.7.10 because of ViaVersion
                // Ascending power rails in 1.7 have flat rail hitbox https://bugs.mojang.com/browse/MC-9134
                if (railType == PacketStateTypes.ACTIVATOR_RAIL || (railType == PacketStateTypes.POWERED_RAIL && data.isPowered())) {
                    yield new SimpleCollisionBox(0.0F, 0.0F, 0.0F, 1.0F, 0.125F, 1.0F, false);
                }
                yield new SimpleCollisionBox(0.0F, 0.0F, 0.0F, 1.0F, 0.625F, 1.0F, false);
            } else if (version.isOlderThan(PacketClientVersions.V_1_9)) {
                yield new SimpleCollisionBox(0.0F, 0.0F, 0.0F, 1.0F, 0.625F, 1.0F, false);
            } else if (version.isNewerThanOrEquals(PacketClientVersions.V_1_9) && version.isOlderThan(PacketClientVersions.V_1_10)) {
                // https://bugs.mojang.com/browse/MC-89552 sloped rails in 1.9 - it is slightly taller than a regular rail
                yield new SimpleCollisionBox(0.0F, 0.0F, 0.0F, 1.0F, 0.1875F, 1.0F, false);
            } else if (version.isOlderThan(PacketClientVersions.V_1_11)) {
                // https://bugs.mojang.com/browse/MC-102638 All sloped rails are full blocks in 1.10
                yield new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true);
            }
            yield new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
        }
        default -> new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
    }, BlockTags.RAILS.getStates().toArray(new PacketStateType[0])),

    END_PORTAL((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (version.isOlderThan(PacketClientVersions.V_1_9)) {
            return new SimpleCollisionBox(0.0D, 0.0D, 0.0D, 1.0D, 0.0625D, 1.0D);
        } else if (version.isOlderThan(PacketClientVersions.V_1_17)) {
            return new SimpleCollisionBox(0.0, 0.0D, 0.0D, 1.0D, 0.75D, 1.0D);
        }
        return new HexCollisionBox(0.0D, 6.0D, 0.0D, 16.0D, 12.0D, 16.0D);
    }, PacketStateTypes.END_PORTAL),

    FENCE_GATE((player, item, version, data, isTargetBlock, x, y, z) -> {
        // This technically should be taken from the block data/made multi-version/run block updates... but that's too far even for me
        // This way is so much easier and works unless the magic stick wand is used
        boolean isInWall;
        boolean isXAxis = data.facing() == BlockFace.WEST || data.facing() == BlockFace.EAST;
        if (isXAxis) {
            boolean zPosWall = Materials.isWall(player.compensatedWorld.getBlockType(x, y, z + 1));
            boolean zNegWall = Materials.isWall(player.compensatedWorld.getBlockType(x, y, z - 1));
            isInWall = zPosWall || zNegWall;
        } else {
            boolean xPosWall = Materials.isWall(player.compensatedWorld.getBlockType(x + 1, y, z));
            boolean xNegWall = Materials.isWall(player.compensatedWorld.getBlockType(x - 1, y, z));
            isInWall = xPosWall || xNegWall;
        }

        if (isInWall) {
            return isXAxis ? new HexCollisionBox(6.0D, 0.0D, 0.0D, 10.0D, 13.0D, 16.0D) : new HexCollisionBox(0.0D, 0.0D, 6.0D, 16.0D, 13.0D, 10.0D);
        }

        return isXAxis ? new HexCollisionBox(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D) : new HexCollisionBox(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    }, BlockTags.FENCE_GATES.getStates().toArray(new PacketStateType[0])),


    FENCE(new DynamicHitboxFence(), BlockTags.FENCES.getStates().toArray(new PacketStateType[0])),

    PANE(new DynamicHitboxPane(), Materials.getPanes().toArray(new PacketStateType[0])),

    LEVER(((player, item, version, data, isTargetBlock, x, y, z) -> {
        Face face = data.face();
        BlockFace facing = data.facing();
        if (version.isOlderThan(PacketClientVersions.V_1_13)) {
            double f = 0.1875;

            switch (face) {
                case WALL:
                    switch (facing) {
                        case WEST:
                            return new SimpleCollisionBox(1.0 - f * 2.0, 0.2, 0.5 - f, 1.0, 0.8, 0.5 + f, false);
                        case EAST:
                            return new SimpleCollisionBox(0.0, 0.2, 0.5 - f, f * 2.0, 0.8, 0.5 + f, false);
                        case NORTH:
                            return new SimpleCollisionBox(0.5 - f, 0.2, 1.0 - f * 2.0, 0.5 + f, 0.8, 1.0, false);
                        case SOUTH:
                            return new SimpleCollisionBox(0.5 - f, 0.2, 0.0, 0.5 + f, 0.8, f * 2.0, false);
                    }
                case CEILING:
                    return new SimpleCollisionBox(0.25, 0.4, 0.25, 0.75, 1.0, 0.75, false);
                case FLOOR:
                    return new SimpleCollisionBox(0.25, 0.0, 0.25, 0.75, 0.6, 0.75, false);
            }
        }

        return switch (face) {
            case FLOOR -> {
                // X-AXIS
                if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
                    yield new SimpleCollisionBox(0.25, 0.0, 0.3125, 0.75, 0.375, 0.6875, false);
                }
                // Z-AXIS
                yield new SimpleCollisionBox(0.3125, 0.0, 0.25, 0.6875, 0.375, 0.75, false);
                // Z-AXIS
            }
            case WALL -> switch (facing) {
                case EAST -> new SimpleCollisionBox(0.0, 0.25, 0.3125, 0.375, 0.75, 0.6875, false);
                case WEST -> new SimpleCollisionBox(0.625, 0.25, 0.3125, 1.0, 0.75, 0.6875, false);
                case SOUTH -> new SimpleCollisionBox(0.3125, 0.25, 0.0, 0.6875, 0.75, 0.375, false);
                default -> new SimpleCollisionBox(0.3125, 0.25, 0.625, 0.6875, 0.75, 1.0, false);
            };
            default -> {
                // X-AXIS
                if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
                    yield new SimpleCollisionBox(0.25, 0.625, 0.3125, 0.75, 1.0, 0.6875, false);
                }
                // Z-Axis
                yield new SimpleCollisionBox(0.3125, 0.625, 0.25, 0.6875, 1.0, 0.75, false);
            }
        };
    }), PacketStateTypes.LEVER),

    BUTTON((player, item, version, data, isTargetBlock, x, y, z) -> {
        final Face face = data.face();
        final BlockFace facing = data.facing();
        final boolean powered = data.isPowered();


        if (version.isOlderThan(PacketClientVersions.V_1_13)) {
            double f2 = (float) (data.isPowered() ? 1 : 2) / 16.0;

            switch (face) {
                case WALL:
                    switch (facing) {
                        case WEST:
                            return new SimpleCollisionBox(1.0 - f2, 0.375, 0.3125, 1.0, 0.625, 0.6875, false);
                        case EAST:
                            return new SimpleCollisionBox(0.0, 0.375, 0.3125, f2, 0.625, 0.6875, false);
                        case NORTH:
                            return new SimpleCollisionBox(0.3125, 0.375, 1.0 - f2, 0.6875, 0.625, 1.0, false);
                        case SOUTH:
                            return new SimpleCollisionBox(0.3125, 0.375, 0.0, 0.6875, 0.625, f2, false);
                    }
                case CEILING:
                    return new SimpleCollisionBox(0.3125, 1.0 - f2, 0.375, 0.6875, 1.0, 0.625, false);
                case FLOOR:
                    return new SimpleCollisionBox(0.3125, 0.0, 0.375, 0.6875, 0.0 + f2, 0.625, false);
            }
        }


        switch (face) {
            case WALL:
                return switch (facing) {
                    case EAST ->
                            powered ? new HexCollisionBox(0.0, 6.0, 5.0, 1.0, 10.0, 11.0) : new HexCollisionBox(0.0, 6.0, 5.0, 2.0, 10.0, 11.0);
                    case WEST ->
                            powered ? new HexCollisionBox(15.0, 6.0, 5.0, 16.0, 10.0, 11.0) : new HexCollisionBox(14.0, 6.0, 5.0, 16.0, 10.0, 11.0);
                    case SOUTH ->
                            powered ? new HexCollisionBox(5.0, 6.0, 0.0, 11.0, 10.0, 1.0) : new HexCollisionBox(5.0, 6.0, 0.0, 11.0, 10.0, 2.0);
                    case NORTH, UP, DOWN ->
                            powered ? new HexCollisionBox(5.0, 6.0, 15.0, 11.0, 10.0, 16.0) : new HexCollisionBox(5.0, 6.0, 14.0, 11.0, 10.0, 16.0);
                    default -> NoCollisionBox.INSTANCE;
                };
            case CEILING:
                // ViaVersion shows lever
                if (player.getClientVersion().isOlderThan(PacketClientVersions.V_1_8)) {
                    return LEVER.dynamic.fetch(player, item, version, data, isTargetBlock, x, y, z);
                }
                // x axis
                if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
                    return powered ? new HexCollisionBox(6.0, 15.0, 5.0, 10.0, 16.0, 11.0) : new HexCollisionBox(6.0, 14.0, 5.0, 10.0, 16.0, 11.0);
                } else {
                    return powered ? new HexCollisionBox(5.0, 15.0, 6.0, 11.0, 16.0, 10.0) : new HexCollisionBox(5.0, 14.0, 6.0, 11.0, 16.0, 10.0);
                }
            case FLOOR:
                // ViaVersion shows lever
                if (player.getClientVersion().isOlderThan(PacketClientVersions.V_1_8)) {
                    return LEVER.dynamic.fetch(player, item, version, data, isTargetBlock, x, y, z);
                }
                // x axis
                if (facing == BlockFace.EAST || facing == BlockFace.WEST) {
                    return powered ? new HexCollisionBox(6.0, 0.0, 5.0, 10.0, 1.0, 11.0) : new HexCollisionBox(6.0, 0.0, 5.0, 10.0, 2.0, 11.0);
                }

                return powered ? new HexCollisionBox(5.0, 0.0, 6.0, 11.0, 1.0, 10.0) : new HexCollisionBox(5.0, 0.0, 6.0, 11.0, 2.0, 10.0);
            default:
                throw new IllegalStateException();
        }
    }, BlockTags.BUTTONS.getStates().toArray(new PacketStateType[0])),

    WALL(new DynamicHitboxWall(), BlockTags.WALLS.getStates().toArray(new PacketStateType[0])),

    WALL_SIGN((player, item, version, data, isTargetBlock, x, y, z) -> switch (data.facing()) {
        case NORTH -> new HexCollisionBox(0.0, 4.5, 14.0, 16.0, 12.5, 16.0);
        case SOUTH -> new HexCollisionBox(0.0, 4.5, 0.0, 16.0, 12.5, 2.0);
        case EAST -> new HexCollisionBox(0.0, 4.5, 0.0, 2.0, 12.5, 16.0);
        case WEST -> new HexCollisionBox(14.0, 4.5, 0.0, 16.0, 12.5, 16.0);
        default -> NoCollisionBox.INSTANCE;
    }, BlockTags.WALL_SIGNS.getStates().toArray(new PacketStateType[0])),

    WALL_HANGING_SIGN((player, item, version, data, isTargetBlock, x, y, z) -> switch (data.facing()) {
        case NORTH, SOUTH -> new ComplexCollisionBox(2,
                new HexCollisionBox(0.0D, 14.0D, 6.0D, 16.0D, 16.0D, 10.0D),
                new HexCollisionBox(1.0D, 0.0D, 7.0D, 15.0D, 10.0D, 9.0D));
        default -> new ComplexCollisionBox(2,
                new HexCollisionBox(6.0D, 14.0D, 0.0D, 10.0D, 16.0D, 16.0D),
                new HexCollisionBox(7.0D, 0.0D, 1.0D, 9.0D, 10.0D, 15.0D));
    }, BlockTags.WALL_HANGING_SIGNS.getStates().toArray(new PacketStateType[0])),

    STANDING_SIGN((player, item, version, data, isTargetBlock, x, y, z) ->
            new HexCollisionBox(4.0, 0.0, 4.0, 12.0, 16.0, 12.0),
            BlockTags.STANDING_SIGNS.getStates().toArray(new PacketStateType[0])),

    SAPLING(new HexCollisionBox(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D),
            BlockTags.SAPLINGS.getStates().toArray(new PacketStateType[0])),

    ROOTS(new HexCollisionBox(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D),
            PacketStateTypes.WARPED_ROOTS, PacketStateTypes.CRIMSON_ROOTS),

    BANNER(new HexCollisionBox(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D),
            PacketStateTypes.WHITE_BANNER, PacketStateTypes.ORANGE_BANNER, PacketStateTypes.MAGENTA_BANNER, PacketStateTypes.LIGHT_BLUE_BANNER,
            PacketStateTypes.YELLOW_BANNER, PacketStateTypes.LIME_BANNER, PacketStateTypes.PINK_BANNER, PacketStateTypes.GRAY_BANNER,
            PacketStateTypes.LIGHT_GRAY_BANNER, PacketStateTypes.CYAN_BANNER, PacketStateTypes.PURPLE_BANNER, PacketStateTypes.BLUE_BANNER,
            PacketStateTypes.BROWN_BANNER, PacketStateTypes.GREEN_BANNER, PacketStateTypes.RED_BANNER, PacketStateTypes.BLACK_BANNER),

    WALL_BANNER((player, item, version, data, isTargetBlock, x, y, z) -> {
        // ViaVersion replacement block
        if (version.isOlderThan(PacketClientVersions.V_1_8)) {
            return WALL_SIGN.dynamic.fetch(player, item, version, data, isTargetBlock, x, y, z);
        }

        return switch (data.facing()) {
            case NORTH -> new HexCollisionBox(0.0, 0.0, 14.0, 16.0, 12.5, 16.0);
            case EAST -> new HexCollisionBox(0.0, 0.0, 0.0, 2.0, 12.5, 16.0);
            case WEST -> new HexCollisionBox(14.0, 0.0, 0.0, 16.0, 12.5, 16.0);
            case SOUTH -> new HexCollisionBox(0.0, 0.0, 0.0, 16.0, 12.5, 2.0);
            default ->
                    throw new IllegalStateException("Impossible Banner Facing State; Something very wrong is going on");
        };
    }, PacketStateTypes.WHITE_WALL_BANNER, PacketStateTypes.ORANGE_WALL_BANNER, PacketStateTypes.MAGENTA_WALL_BANNER,
            PacketStateTypes.LIGHT_BLUE_WALL_BANNER, PacketStateTypes.YELLOW_WALL_BANNER, PacketStateTypes.LIME_WALL_BANNER,
            PacketStateTypes.PINK_WALL_BANNER, PacketStateTypes.GRAY_WALL_BANNER, PacketStateTypes.LIGHT_GRAY_WALL_BANNER,
            PacketStateTypes.CYAN_WALL_BANNER, PacketStateTypes.PURPLE_WALL_BANNER, PacketStateTypes.BLUE_WALL_BANNER,
            PacketStateTypes.BROWN_WALL_BANNER, PacketStateTypes.GREEN_WALL_BANNER, PacketStateTypes.RED_WALL_BANNER, PacketStateTypes.BLACK_WALL_BANNER),

    BREWING_STAND((player, item, version, block, isTargetBlock, x, y, z) -> {
        // BEWARE OF https://bugs.mojang.com/browse/MC-85109 FOR 1.8 PLAYERS
        // 1.8 Brewing Stand hitbox is a fullblock until it is hit sometimes, can be caused be restarting client and joining server
        if (version.isOlderThan(PacketClientVersions.V_1_13)) {
            if (isTargetBlock && block.getType() == PacketStateTypes.BREWING_STAND && player.getClientVersion().equals(PacketClientVersions.V_1_8)) {
                return new ComplexCollisionBox(2,
                        new SimpleCollisionBox(0.0F, 0.0F, 0.0F, 1.0F, 0.125F, 1.0F),
                        new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true)
                );
            }
            return new SimpleCollisionBox(0.0F, 0.0F, 0.0F, 1.0F, 0.125F, 1.0F);
        } else {
            return new ComplexCollisionBox(2,
                    new HexCollisionBox(1.0, 0.0, 1.0, 15.0, 2.0, 15.0),
                    new SimpleCollisionBox(0.4375, 0.0, 0.4375, 0.5625, 0.875, 0.5625, false));
        }
    }, PacketStateTypes.BREWING_STAND),

    SMALL_FLOWER((player, item, version, data, isTargetBlock, x, y, z) -> player.getClientVersion().isOlderThan(PacketClientVersions.V_1_13)
            ? new SimpleCollisionBox(0.3125D, 0.0D, 0.3125D, 0.6875D, 0.625D, 0.6875D)
            : new OffsetCollisionBox(data.getType(), 0.3125D, 0.0D, 0.3125D, 0.6875D, 0.625D, 0.6875D),
            BlockTags.SMALL_FLOWERS.getStates().toArray(new PacketStateType[0])),

    TALL_FLOWERS(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true), BlockTags.TALL_FLOWERS.getStates().toArray(new PacketStateType[0])),

    FIRE((player, item, version, data, isTargetBlock, x, y, z) -> {
        // Since 1.16 fire has a small hitbox
        if (version.isNewerThanOrEquals(PacketClientVersions.V_1_16)) {
            return new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);
        }
        return NoCollisionBox.INSTANCE;
    }, BlockTags.FIRE.getStates().toArray(new PacketStateType[0])),

    HONEY_BLOCK(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true), PacketStateTypes.HONEY_BLOCK),

    POWDER_SNOW(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true), PacketStateTypes.POWDER_SNOW),

    SOUL_SAND(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true), PacketStateTypes.SOUL_SAND),

    CACTUS((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (version.isOlderThan(PacketClientVersions.V_1_13)) {
            // https://bugs.mojang.com/browse/MC-59610
            return new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true);
        }
        return new HexCollisionBox(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);
    }, PacketStateTypes.CACTUS),

    SNOW((player, item, version, data, isTargetBlock, x, y, z) -> new SimpleCollisionBox(0, 0, 0, 1, data.getLayers() * 0.125, 1), PacketStateTypes.SNOW),

    LECTERN_BLOCK((player, item, version, data, isTargetBlock, x, y, z) -> {
        ComplexCollisionBox common = new ComplexCollisionBox(5,
                new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
                new HexCollisionBox(4.0D, 2.0D, 4.0D, 12.0D, 14.0D, 12.0D));

        if (data.facing() == BlockFace.WEST) {
            common.add(new HexCollisionBox(1.0D, 10.0D, 0.0D, 5.333333D, 14.0D, 16.0D));
            common.add(new HexCollisionBox(5.333333D, 12.0D, 0.0D, 9.666667D, 16.0D, 16.0D));
            common.add(new HexCollisionBox(9.666667D, 14.0D, 0.0D, 14.0D, 18.0D, 16.0D));
        } else if (data.facing() == BlockFace.NORTH) {
            common.add(new HexCollisionBox(0.0D, 10.0D, 1.0D, 16.0D, 14.0D, 5.333333D));
            common.add(new HexCollisionBox(0.0D, 12.0D, 5.333333D, 16.0D, 16.0D, 9.666667D));
            common.add(new HexCollisionBox(0.0D, 14.0D, 9.666667D, 16.0D, 18.0D, 14.0D));
        } else if (data.facing() == BlockFace.EAST) {
            common.add(new HexCollisionBox(10.666667D, 10.0D, 0.0D, 15.0D, 14.0D, 16.0D));
            common.add(new HexCollisionBox(6.333333D, 12.0D, 0.0D, 10.666667D, 16.0D, 16.0D));
            common.add(new HexCollisionBox(2.0D, 14.0D, 0.0D, 6.333333D, 18.0D, 16.0D));
        } else { // SOUTH
            common.add(new HexCollisionBox(0.0D, 10.0D, 10.666667D, 16.0D, 14.0D, 15.0D));
            common.add(new HexCollisionBox(0.0D, 12.0D, 6.333333D, 16.0D, 16.0D, 10.666667D));
            common.add(new HexCollisionBox(0.0D, 14.0D, 2.0D, 16.0D, 18.0D, 6.333333D));
        }

        return common;
    }, PacketStateTypes.LECTERN),

    GLOW_LICHEN_SCULK_VEIN((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (version.isNewerThan(PacketClientVersions.V_1_16_4)) {
            ComplexCollisionBox box = new ComplexCollisionBox(6);

            if (data.isUp()) {
                box.add(new HexCollisionBox(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D));
            }
            if (data.isDown()) {
                box.add(new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D));
            }
            if (data.west() == West.TRUE) {
                box.add(new HexCollisionBox(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D));
            }
            if (data.east() == East.TRUE) {
                box.add(new HexCollisionBox(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D));
            }
            if (data.north() == North.TRUE) {
                box.add(new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D));
            }
            if (data.south() == South.TRUE) {
                box.add(new HexCollisionBox(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D));
            }

            return box;
        } else { // ViaVersion just replaces this with... nothing
            return NoCollisionBox.INSTANCE;
        }
    }, PacketStateTypes.GLOW_LICHEN, PacketStateTypes.SCULK_VEIN, PacketStateTypes.RESIN_CLUMP),

    SPORE_BLOSSOM((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (version.isNewerThan(PacketClientVersions.V_1_16_4)) {
            return new HexCollisionBox(2.0D, 13.0D, 2.0D, 14.0D, 16.0D, 14.0D);
        } else { // ViaVersion replacement is a Peony
            return new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true);
        }
    }, PacketStateTypes.SPORE_BLOSSOM),

    PITCHER_CROP((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (version.isNewerThan(PacketClientVersions.V_1_19_4)) {
            final SimpleCollisionBox FULL_UPPER_SHAPE = new HexCollisionBox(3.0D, 0.0D, 3.0D, 13.0D, 15.0D, 13.0D);
            final SimpleCollisionBox FULL_LOWER_SHAPE = new HexCollisionBox(3.0D, -1.0D, 3.0D, 13.0D, 16.0D, 13.0D);
            final SimpleCollisionBox COLLISION_SHAPE_BULB = new HexCollisionBox(5.0D, -1.0D, 5.0D, 11.0D, 3.0D, 11.0D);
            final SimpleCollisionBox COLLISION_SHAPE_CROP = new HexCollisionBox(3.0D, -1.0D, 3.0D, 13.0D, 5.0D, 13.0D);
            final SimpleCollisionBox[] UPPER_SHAPE_BY_AGE = new SimpleCollisionBox[]{new HexCollisionBox(3.0D, 0.0D, 3.0D, 13.0D, 11.0D, 13.0D), FULL_UPPER_SHAPE};
            final SimpleCollisionBox[] LOWER_SHAPE_BY_AGE = new SimpleCollisionBox[]{COLLISION_SHAPE_BULB, new HexCollisionBox(3.0D, -1.0D, 3.0D, 13.0D, 14.0D, 13.0D), FULL_LOWER_SHAPE, FULL_LOWER_SHAPE, FULL_LOWER_SHAPE};

            return data.half() == Half.UPPER ? UPPER_SHAPE_BY_AGE[Math.min(Math.abs(4 - (data.getAge() + 1)), UPPER_SHAPE_BY_AGE.length - 1)] : LOWER_SHAPE_BY_AGE[data.getAge()];
        } else {
            return new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true);
        }
    }, PacketStateTypes.PITCHER_CROP),

    WHEAT_BEETROOTS((player, item, version, data, isTargetBlock, x, y, z) ->
            new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, (data.getAge() + 1) * 2, 16.0D), PacketStateTypes.WHEAT, PacketStateTypes.BEETROOTS),

    CARROT_POTATOES((player, item, version, data, isTargetBlock, x, y, z) ->
            new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, data.getAge() + 2, 16.0D), PacketStateTypes.CARROTS, PacketStateTypes.POTATOES),

    NETHER_WART((player, item, version, data, isTargetBlock, x, y, z) ->
            new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0, 5 + (data.getAge() * 3), 16.0D), PacketStateTypes.NETHER_WART),

    ATTACHED_PUMPKIN_STEM((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (version.isOlderThan(PacketClientVersions.V_1_13))
            return new HexCollisionBox(7.0D, 0.0D, 7.0D, 9.0D, 16.0D, 9.0D);

        return switch (data.facing()) {
            case SOUTH -> new HexCollisionBox(6.0D, 0.0D, 6.0D, 10.0D, 10.0D, 16.0D);
            case WEST -> new HexCollisionBox(0.0D, 0.0D, 6.0D, 10.0D, 10.0D, 10.0D);
            case NORTH -> new HexCollisionBox(6.0D, 0.0D, 0.0D, 10.0D, 10.0D, 10.0D);
            default -> new HexCollisionBox(6.0D, 0.0D, 6.0D, 16.0D, 10.0D, 10.0D);
        };
    }, PacketStateTypes.ATTACHED_MELON_STEM, PacketStateTypes.ATTACHED_PUMPKIN_STEM),

    PUMPKIN_STEM((player, item, version, data, isTargetBlock, x, y, z) ->
            new HexCollisionBox(7, 0, 7, 9, 2 * (data.getAge() + 1), 9), PacketStateTypes.PUMPKIN_STEM, PacketStateTypes.MELON_STEM),

    // Hitbox/Outline is Same as Collision
    COCOA_BEANS((player, item, version, data, isTargetBlock, x, y, z) ->
            CollisionData.getCocoa(version, data.getAge(), data.facing()), PacketStateTypes.COCOA),

    // Easier to just use no collision box
    // Redstone wire is very complex with its collision shapes and has many de-syncs
    REDSTONE_WIRE(NoCollisionBox.INSTANCE, PacketStateTypes.REDSTONE_WIRE),

    SWEET_BERRY((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (data.getAge() == 0) {
            return new HexCollisionBox(3.0D, 0.0D, 3.0D, 13.0D, 8.0D, 13.0D);
        } else if (data.getAge() < 3) {
            return new HexCollisionBox(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);
        }
        return new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true);
    }, PacketStateTypes.SWEET_BERRY_BUSH),

    CORAL_FAN(new HexCollisionBox(2.0D, 0.0D, 2.0D, 14.0D, 4.0D, 14.0D), BlockTags.CORALS.getStates().toArray(new PacketStateType[0])),

    TORCHFLOWER_CROP((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (data.getAge() == 0) {
            return new HexCollisionBox(5.0D, 0.0D, 5.0D, 11.0D, 6.0D, 11.0D);
        }
        // age is 1
        return new HexCollisionBox(5.0D, 0.0D, 5.0D, 11.0D, 10.0D, 11.0D);
    }, PacketStateTypes.TORCHFLOWER_CROP),

    DEAD_BUSH(new HexCollisionBox(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D), PacketStateTypes.DEAD_BUSH),

    SUGARCANE(new HexCollisionBox(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D), PacketStateTypes.SUGAR_CANE),

    NETHER_SPROUTS(new HexCollisionBox(2.0D, 0.0D, 2.0D, 14.0D, 3.0D, 14.0D), PacketStateTypes.NETHER_SPROUTS),

    HANGING_ROOTS(new HexCollisionBox(2.0D, 10.0D, 2.0D, 14.0D, 16.0D, 14.0D), PacketStateTypes.HANGING_ROOTS),

    HANGING_MOSS((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (version.isOlderThan(PacketClientVersions.V_1_21_2)) {
            return HANGING_ROOTS.fetch(player, item, version, data, isTargetBlock, x, y, z);
        }
        return new HexCollisionBox(1, data.isTip() ? 2 : 0, 1, 15, 16, 15);
    }, PacketStateTypes.PALE_HANGING_MOSS),

    GRASS_FERN((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (version.isOlderThan(PacketClientVersions.V_1_13)) {
            return new SimpleCollisionBox(0.1F, 0.0F, 0.1F, 0.9F, 0.8F, 0.9F);
        }
        return new HexCollisionBox(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D);
    }, PacketStateTypes.SHORT_GRASS, PacketStateTypes.FERN),

    SEA_GRASS(new HexCollisionBox(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D),
            PacketStateTypes.SEAGRASS),

    TALL_SEAGRASS(new HexCollisionBox(2.0, 0.0, 2.0, 14.0, 16.0, 14.0),
            PacketStateTypes.TALL_SEAGRASS),

    SMALL_DRIPLEAF(new HexCollisionBox(2.0, 0.0, 2.0, 14.0, 13.0, 14.0), PacketStateTypes.SMALL_DRIPLEAF),

    CAVE_VINES(new HexCollisionBox(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D), PacketStateTypes.CAVE_VINES, PacketStateTypes.CAVE_VINES_PLANT),

    // Then your enum entries become:
    TWISTING_VINES_BLOCK((player, item, version, data, isTargetBlock, x, y, z) ->
            getVineCollisionBox(version, false, true), PacketStateTypes.TWISTING_VINES),

    WEEPING_VINES_BLOCK((player, item, version, data, isTargetBlock, x, y, z) ->
            getVineCollisionBox(version, true, true), PacketStateTypes.WEEPING_VINES),

    TWISTING_VINES((player, item, version, data, isTargetBlock, x, y, z) ->
            getVineCollisionBox(version, false, false), PacketStateTypes.TWISTING_VINES_PLANT),

    WEEPING_VINES((player, item, version, data, isTargetBlock, x, y, z) ->
            getVineCollisionBox(version, true, false), PacketStateTypes.WEEPING_VINES_PLANT),

    TALL_PLANT(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true), PacketStateTypes.TALL_GRASS, PacketStateTypes.LARGE_FERN),

    BAMBOO((player, item, version, data, isTargetBlock, x, y, z) -> data.leaves() == Leaves.LARGE
            ? new HexOffsetCollisionBox(data.getType(), 3.0, 0.0, 3.0, 13.0, 16.0, 13.0)
            : new HexOffsetCollisionBox(data.getType(), 5.0, 0.0, 5.0, 11.0, 16.0, 11.0), PacketStateTypes.BAMBOO),

    BAMBOO_SAPLING((player, item, version, data, isTargetBlock, x, y, z) ->
            new HexOffsetCollisionBox(data.getType(), 4.0D, 0.0D, 4.0D, 12.0D, 12.0D, 12.0D), PacketStateTypes.BAMBOO_SAPLING),

    SCAFFOLDING((player, item, version, data, isTargetBlock, x, y, z) -> {
        // If is holding scaffolding or Via replacement - hay bale
        if (item == PacketStateTypes.SCAFFOLDING || version.isOlderThan(PacketClientVersions.V_1_14)) {
            return new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true);
        }

        // STABLE_SHAPE for the scaffolding
        ComplexCollisionBox box = new ComplexCollisionBox(9,
                new HexCollisionBox(0.0D, 14.0D, 0.0D, 16.0D, 16.0D, 16.0D),
                new HexCollisionBox(0.0D, 0.0D, 0.0D, 2.0D, 16.0D, 2.0D),
                new HexCollisionBox(14.0D, 0.0D, 0.0D, 16.0D, 16.0D, 2.0D),
                new HexCollisionBox(0.0D, 0.0D, 14.0D, 2.0D, 16.0D, 16.0D),
                new HexCollisionBox(14.0D, 0.0D, 14.0D, 16.0D, 16.0D, 16.0D));

        if (data.half() == Half.LOWER) { // Add the unstable shape to the collision boxes
            box.add(new HexCollisionBox(0.0D, 0.0D, 0.0D, 2.0D, 2.0D, 16.0D));
            box.add(new HexCollisionBox(14.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D));
            box.add(new HexCollisionBox(0.0D, 0.0D, 14.0D, 16.0D, 2.0D, 16.0D));
            box.add(new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 2.0D));
        }

        return box;
    }, PacketStateTypes.SCAFFOLDING),

    DRIPLEAF((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (version.isOlderThanOrEquals(PacketClientVersions.V_1_16_4))
            return new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true);

        ComplexCollisionBox box = new ComplexCollisionBox(2);

        if (data.facing() == BlockFace.NORTH) { // Stem
            box.add(new HexCollisionBox(5.0D, 0.0D, 9.0D, 11.0D, 15.0D, 15.0D));
        } else if (data.facing() == BlockFace.SOUTH) {
            box.add(new HexCollisionBox(5.0D, 0.0D, 1.0D, 11.0D, 15.0D, 7.0D));
        } else if (data.facing() == BlockFace.EAST) {
            box.add(new HexCollisionBox(1.0D, 0.0D, 5.0D, 7.0D, 15.0D, 11.0D));
        } else {
            box.add(new HexCollisionBox(9.0D, 0.0D, 5.0D, 15.0D, 15.0D, 11.0D));
        }

        if (data.tilt() == Tilt.NONE || data.tilt() == Tilt.UNSTABLE) {
            box.add(new HexCollisionBox(0.0, 11.0, 0.0, 16.0, 15.0, 16.0));
        } else if (data.tilt() == Tilt.PARTIAL) {
            box.add(new HexCollisionBox(0.0, 11.0, 0.0, 16.0, 13.0, 16.0));
        }

        return box;

    }, PacketStateTypes.BIG_DRIPLEAF),

    PINK_PETALS_BLOCK((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (version.isNewerThan(PacketClientVersions.V_1_20_2)) {
            return getFlowerBedHitBox(data.getFlowerAmount(), data.facing().getHorizontalId());
        } else if (version.isNewerThan(PacketClientVersions.V_1_19_3)) {
            return new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);
        } else if (version.isNewerThan(PacketClientVersions.V_1_12_2)) {
            return CORAL_FAN.box.copy();
        }
        return GRASS_FERN.dynamic.fetch(player, item, version, data, isTargetBlock, x, y, z);
    }, PacketStateTypes.PINK_PETALS),

    MANGROVE_PROPAGULE(((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (data.isHanging()) {
            return new HexOffsetCollisionBox(data.getType(), 7.0, 0.0, 7.0, 9.0, 16.0, 9.0);
        } else {
            return new HexOffsetCollisionBox(data.getType(), 7.0, getPropaguleMinHeight(data.getAge()), 7.0, 9.0, 16.0, 9.0);
        }
    }), PacketStateTypes.MANGROVE_PROPAGULE),

    FROGSPAWN((player, item, version, data, isTargetBlock, x, y, z) -> {
        if (version.isNewerThan(PacketClientVersions.V_1_18_2)) {
            return new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, 1.5D, 16.0D);
        } else { // ViaVersion just replaces this with... nothing
            return NoCollisionBox.INSTANCE;
        }
    }, PacketStateTypes.FROGSPAWN),

    BUSH((player, heldItem, version, block, isTargetBlock, x, y, z)
            -> version.isNewerThan(PacketClientVersions.V_1_21_4)
            ? new SimpleCollisionBox(0, 0, 0, 1, 0.8125, 1)
            : GRASS_FERN.dynamic.fetch(player, heldItem, version, block, isTargetBlock, x, y, z), PacketStateTypes.BUSH),

    SHORT_DRY_GRASS((player, heldItem, version, block, isTargetBlock, x, y, z)
            -> version.isNewerThan(PacketClientVersions.V_1_21_4)
            ? new SimpleCollisionBox(0.125, 0, 0.125, 0.875, 0.625, 0.875)
            : GRASS_FERN.dynamic.fetch(player, heldItem, version, block, isTargetBlock, x, y, z), PacketStateTypes.SHORT_DRY_GRASS),

    TALL_DRY_GRASS((player, heldItem, version, block, isTargetBlock, x, y, z)
            -> version.isNewerThan(PacketClientVersions.V_1_21_4)
            ? new SimpleCollisionBox(0.0625, 0, 0.0625, 0.9375, 1, 0.9375)
            : GRASS_FERN.dynamic.fetch(player, heldItem, version, block, isTargetBlock, x, y, z), PacketStateTypes.TALL_DRY_GRASS),

    LEAF_LITTER((player, item, version, data, isTargetBlock, x, y, z)
            -> version.isNewerThan(PacketClientVersions.V_1_21_4)
            ? getFlowerBedHitBox(data.getSegmentAmount(), data.facing().getHorizontalId())
            // GLOW_LICHEN Facing Upwards, can't call glow lichen dynamic because data has no isUp() key
            : new HexCollisionBox(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D), PacketStateTypes.LEAF_LITTER),

    WILDFLOWERS((player, item, version, data, isTargetBlock, x, y, z)
            -> version.isNewerThan(PacketClientVersions.V_1_21_4)
            ? getFlowerBedHitBox(data.getFlowerAmount(), data.facing().getHorizontalId())
            // GLOW_LICHEN Facing Upwards, can't call glow lichen dynamic because data has no isUp() key
            : new HexCollisionBox(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D), PacketStateTypes.WILDFLOWERS),

    CACTUS_FLOWER((player, item, version, data, isTargetBlock, x, y, z)
            -> version.isNewerThan(PacketClientVersions.V_1_21_4)
            ? new SimpleCollisionBox(0.0625, 0, 0.0625, 0.9375, 0.75, 0.9375)
            : CORAL_FAN.box.copy(), PacketStateTypes.CACTUS_FLOWER),

    // always a fullblock hitbox. Via replacement is obsidian
    SCULK_SHRIEKER(new SimpleCollisionBox(0, 0, 0, 1, 1, 1, true), PacketStateTypes.SCULK_SHRIEKER);

    private static final Map<PacketStateType, HitboxData> lookup = new HashMap<>();

    static {
        for (HitboxData data : HitboxData.values()) {
            for (PacketStateType type : data.materials) {
                lookup.put(type, data);
            }
        }
    }

    private final PacketStateType[] materials;
    private CollisionBox box;
    private HitBoxFactory dynamic;

    HitboxData(CollisionBox box, PacketStateType... materials) {
        this.box = box;
        Set<PacketStateType> mList = new HashSet<>(Arrays.asList(materials));
        mList.remove(null); // Sets can contain one null
        this.materials = mList.toArray(new PacketStateType[0]);
    }

    HitboxData(HitBoxFactory dynamic, PacketStateType... materials) {
        this.dynamic = dynamic;
        Set<PacketStateType> mList = new HashSet<>(Arrays.asList(materials));
        mList.remove(null); // Sets can contain one null
        this.materials = mList.toArray(new PacketStateType[0]);
    }

    @Override
    public CollisionBox fetch(GrimPlayer player, PacketStateType heldItem, PacketClientVersion version, PacketBlockState block, boolean isTargetBlock, int x, int y, int z) {
        return box != null ? box.copy() : dynamic.fetch(player, heldItem, version, block, isTargetBlock, x, y, z);
    }

    public static HitboxData getData(PacketStateType material) {
        return lookup.get(material);
    }

    public static CollisionBox getBlockHitbox(GrimPlayer player, PacketStateType heldItem, PacketClientVersion version, PacketBlockState block, boolean isTargetBlock, int x, int y, int z) {
        HitboxData data = getData(block.getType());

        if (data == null) {
            // Fall back to collision boxes
            return CollisionData.getRawData(block.getType()).getMovementCollisionBox(player, version, block, x, y, z);
        }

        return data.fetch(player, heldItem, version, block, isTargetBlock, x, y, z).offset(x, y, z);
    }

    private static int getPropaguleMinHeight(int age) {
        return switch (age) {
            case 0, 1, 2 -> 13 - age * 3;
            case 3, 4 -> (4 - age) * 3;
            default -> throw new IllegalStateException("Impossible Propagule Height");
        };
    }

    private static CollisionBox getVineCollisionBox(PacketClientVersion version, boolean isWeeping, boolean isBlock) {
        if (version.isNewerThan(PacketClientVersions.V_1_15_2)) {
            if (isWeeping) {
                return isBlock
                        ? new HexCollisionBox(4.0, 9.0, 4.0, 12.0, 16.0, 12.0)
                        : new HexCollisionBox(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
            } else {
                return new HexCollisionBox(4.0D, 0.0D, 4.0D, 12.0D, isBlock ? 15.0D : 16.0D, 12.0D);
            }
        } else {
            // Via replacement - 4 sided vine
            return new ComplexCollisionBox(4,
                    new HexCollisionBox(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D),
                    new HexCollisionBox(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
                    new HexCollisionBox(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D),
                    new HexCollisionBox(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D)
            );
        }
    }

    // Pre-defined collision boxes for each quadrant
    private static final HexCollisionBox[] flowerBedHitboxes = new HexCollisionBox[]{
            new HexCollisionBox(8, 0, 8, 16, 3, 16),  // SE
            new HexCollisionBox(8, 0, 0, 16, 3, 8),   // NE
            new HexCollisionBox(0, 0, 0, 8, 3, 8),    // NW
            new HexCollisionBox(0, 0, 8, 8, 3, 16)    // SW
    };
    // TODO, optimize? We don't have to return a CCB and will never return NCB, use SCB.encompass()?
    private static CollisionBox getFlowerBedHitBox(int flowerAmount, int horizontalIndex) {
        CollisionBox result = flowerAmount < 2 ? NoCollisionBox.INSTANCE : new ComplexCollisionBox(flowerAmount);

        // Add boxes based on flower amount and facing
        for (int i = 0; i < flowerAmount; i++) {
            int index = Math.floorMod(i - horizontalIndex, 4);
            result = result.union(flowerBedHitboxes[index]);
        }
        return result;
    }
}
