package ac.grim.grimac.utils.blockplace;

import ac.grim.grimac.api.packet.block.PacketBlockState;
import ac.grim.grimac.api.packet.item.PacketItemType;
import ac.grim.grimac.api.packet.item.PacketItemTypes;
import ac.grim.grimac.api.packet.item.PacketStateType;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.world.PacketStateTypes;
import ac.grim.grimac.api.packet.world.enums.*;
import ac.grim.grimac.events.packets.CheckManagerListener;
import ac.grim.grimac.utils.anticheat.update.BlockPlace;
import ac.grim.grimac.utils.blockstate.helper.BlockFaceHelper;
import ac.grim.grimac.utils.collisions.CollisionData;
import ac.grim.grimac.utils.collisions.datatypes.CollisionBox;
import ac.grim.grimac.utils.latency.CompensatedWorld;
import ac.grim.grimac.api.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.Dripstone;
import ac.grim.grimac.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.ItemTags;
import ac.grim.grimac.api.packet.world.enums.Hinge;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;
import com.github.retrooper.packetevents.util.Vector3i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum BlockPlaceResult {

    // If the block only has directional data
    ANVIL((player, place) -> {
        PacketBlockState data = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
        data.setFacing(BlockFaceHelper.getClockWise(place.getPlayerFacing()));
        place.set(data);
    }, ItemTags.ANVIL),

    // The client only predicts one of the individual bed blocks, interestingly
    BED((player, place) -> {
        // 1.12- players don't predict bed places for some reason
        if (player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_12_2)) return;

        BlockFace facing = place.getPlayerFacing();
        if (place.isBlockFaceOpen(facing)) {
            place.set(place.getMaterial());
        }
    }, ItemTags.BEDS),

    SNOW((player, place) -> {
        Vector3i against = place.getPlacedAgainstBlockLocation();
        PacketBlockState blockState = place.getExistingBlockData();
        int layers = 0;
        if (blockState.getType() == PacketStateTypes.SNOW) {
            layers = blockState.getLayers(); // Indexed at 1
        }

        PacketBlockState below = place.getBelowState();

        if (!BlockTags.ICE.contains(below.getType()) && below.getType() != PacketStateTypes.BARRIER) {
            boolean set = false;
            if (below.getType() != PacketStateTypes.HONEY_BLOCK && below.getType() != PacketStateTypes.SOUL_SAND) {
                if (place.isFullFace(BlockFace.DOWN)) { // Vanilla also checks for 8 layers of snow but that's redundant...
                    set = true;
                }
            } else { // Honey and soul sand are exempt from this full face check
                set = true;
            }

            if (set) {
                if (blockState.getType() == PacketStateTypes.SNOW) {
                    PacketBlockState snow = PacketStateTypes.SNOW.createBlockState(CompensatedWorld.blockVersion);
                    snow.setLayers(Math.min(8, layers + 1));
                    place.set(against, snow);
                } else {
                    place.set();
                }
            }
        }

    }, PacketItemTypes.SNOW),

    SLAB((player, place) -> {
        Vector3dm clickedPos = place.getClickedLocation();

        PacketBlockState slabData = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
        PacketBlockState existing = place.getExistingBlockData();

        if (BlockTags.SLABS.contains(existing.getType())) {
            slabData.setTypeData(Type.DOUBLE);
            place.set(place.getPlacedAgainstBlockLocation(), slabData);
        } else {
            BlockFace direction = place.getDirection();
            boolean clickedTop = direction != BlockFace.DOWN && (direction == BlockFace.UP || !(clickedPos.getY() > 0.5D));
            slabData.setTypeData(clickedTop ? Type.BOTTOM : Type.TOP);
            place.set(slabData);
        }

    }, ItemTags.SLABS),

    STAIRS((player, place) -> {
        BlockFace direction = place.getDirection();
        PacketBlockState stair = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
        stair.setFacing(place.getPlayerFacing());

        Half half = (direction != BlockFace.DOWN && (direction == BlockFace.UP || place.getClickedLocation().getY() < 0.5D)) ? Half.BOTTOM : Half.TOP;
        stair.setHalf(half);
        place.set(stair);
    }, ItemTags.STAIRS),

    END_ROD((player, place) -> {
        PacketBlockState endRod = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
        endRod.setFacing(place.getDirection());
        place.set(endRod);
    }, PacketItemTypes.END_ROD, PacketItemTypes.LIGHTNING_ROD),

    LADDER((player, place) -> {
        //  No placing a ladder against another ladder
        if (!place.isReplaceClicked()) {
            PacketBlockState existing = player.compensatedWorld.getBlock(place.getPlacedAgainstBlockLocation());
            if (existing.getType() == PacketStateTypes.LADDER && existing.getFacing() == place.getDirection()) {
                return;
            }
        }

        for (BlockFace face : place.getNearestPlacingDirections()) {
            // Torches need solid faces
            // Heads have no special preferences - place them anywhere
            // Signs need solid - exempts chorus flowers and a few other strange cases
            if (BlockFaceHelper.isFaceHorizontal(face) && place.isFullFace(face)) {
                PacketBlockState ladder = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
                ladder.setFacing(face.getOppositeFace());
                place.set(ladder);
                return;
            }
        }
    }, PacketItemTypes.LADDER),

    FARM_BLOCK((player, place) -> {
        // What we also need to check:
        PacketBlockState above = place.getAboveState();
        if (!above.getType().isBlocking() && !BlockTags.FENCE_GATES.contains(above.getType()) && above.getType() != PacketStateTypes.MOVING_PISTON) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.FARMLAND),

    // 1.13+ only blocks from here below!  No need to write everything twice
    AMETHYST_CLUSTER((player, place) -> {
        PacketBlockState amethyst = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
        amethyst.setFacing(place.getDirection());
        if (place.isFullFace(place.getDirection().getOppositeFace())) place.set(amethyst);
    }, PacketItemTypes.AMETHYST_CLUSTER, PacketItemTypes.SMALL_AMETHYST_BUD, PacketItemTypes.MEDIUM_AMETHYST_BUD, PacketItemTypes.LARGE_AMETHYST_BUD),

    BAMBOO((player, place) -> {
        Vector3i clicked = place.getPlacedAgainstBlockLocation();
        if (player.compensatedWorld.getFluidLevelAt(clicked.getX(), clicked.getY(), clicked.getZ()) > 0)
            return;

        PacketBlockState below = place.getBelowState();
        if (BlockTags.BAMBOO_PLANTABLE_ON.contains(below.getType())) {
            if (below.getType() == PacketStateTypes.BAMBOO_SAPLING || below.getType() == PacketStateTypes.BAMBOO) {
                place.set(PacketStateTypes.BAMBOO);
            } else {
                PacketBlockState above = place.getBelowState();
                if (above.getType() == PacketStateTypes.BAMBOO_SAPLING || above.getType() == PacketStateTypes.BAMBOO) {
                    place.set(PacketStateTypes.BAMBOO);
                } else {
                    place.set(PacketStateTypes.BAMBOO_SAPLING);
                }
            }
        }
    }, PacketItemTypes.BAMBOO),

    BELL((player, place) -> {
        BlockFace direction = place.getDirection();
        PacketBlockState bell = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);

        boolean canSurvive = !BlockTags.FENCE_GATES.contains(place.getPlacedAgainstMaterial());
        // This is exempt from being able to place on
        if (!canSurvive) return;

        if (place.isFaceVertical()) {
            if (direction == BlockFace.DOWN) {
                bell.setAttachment(Attachment.CEILING);
                canSurvive = place.isFaceFullCenter(BlockFace.UP);
            }
            if (direction == BlockFace.UP) {
                bell.setAttachment(Attachment.FLOOR);
                canSurvive = place.isFullFace(BlockFace.DOWN);
            }
            bell.setFacing(place.getPlayerFacing());
        } else {
            boolean flag = place.isXAxis()
                    && place.isFullFace(BlockFace.EAST)
                    && place.isFullFace(BlockFace.WEST)

                    || place.isZAxis()
                    && place.isFullFace(BlockFace.SOUTH)
                    && place.isFullFace(BlockFace.NORTH);

            bell.setFacing(place.getDirection().getOppositeFace());
            bell.setAttachment(flag ? Attachment.DOUBLE_WALL : Attachment.SINGLE_WALL);
            canSurvive = place.isFullFace(place.getDirection().getOppositeFace());

            if (canSurvive) {
                place.set(bell);
                return;
            }

            boolean flag1 = place.isFullFace(BlockFace.DOWN);
            bell.setAttachment(flag1 ? Attachment.FLOOR : Attachment.CEILING);
            canSurvive = place.isFullFace(flag1 ? BlockFace.DOWN : BlockFace.UP);
        }
        if (canSurvive) place.set(bell);
    }, PacketItemTypes.BELL),

    CANDLE((player, place) -> {
        PacketBlockState existing = place.getExistingBlockData();
        PacketBlockState candle = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);

        if (BlockTags.CANDLES.contains(existing.getType())) {
            // Max candles already exists
            if (existing.getCandles() == 4) return;
            candle.setCandles(existing.getCandles() + 1);
        }

        if (place.isFaceFullCenter(BlockFace.DOWN)) {
            place.set(candle);
        }
    }, ItemTags.CANDLES),

    // Sea pickles refuse to overwrite any collision... but... that's already checked.  Unsure what Mojang is doing.
    SEA_PICKLE((player, place) -> {
        PacketBlockState existing = place.getExistingBlockData();

        if (!place.isFullFace(BlockFace.DOWN) && !place.isFaceEmpty(BlockFace.DOWN)) return;

        if (existing.getType() == PacketStateTypes.SEA_PICKLE) {
            // Max pickels already exist
            if (existing.getPickles() == 4) return;
            existing.setPickles(existing.getPickles() + 1);
        } else {
            existing = PacketStateTypes.SEA_PICKLE.createBlockState(CompensatedWorld.blockVersion);
        }

        place.set(existing);
    }, PacketItemTypes.SEA_PICKLE),

    CHAIN((player, place) -> {
        PacketBlockState chain = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
        BlockFace face = place.getDirection();

        switch (face) {
            case EAST:
            case WEST:
                chain.setAxis(Axis.X);
                break;
            case NORTH:
            case SOUTH:
                chain.setAxis(Axis.Z);
                break;
            case UP:
            case DOWN:
                chain.setAxis(Axis.Y);
                break;
        }

        place.set(chain);
    }, PacketItemTypes.CHAIN),

    COCOA((player, place) -> {
        for (BlockFace face : place.getNearestPlacingDirections()) {
            if (BlockFaceHelper.isFaceVertical(face)) continue;
            PacketStateType mat = place.getDirectionalState(face).getType();
            if (mat == PacketStateTypes.JUNGLE_LOG || mat == PacketStateTypes.STRIPPED_JUNGLE_LOG || mat == PacketStateTypes.JUNGLE_WOOD) {
                PacketBlockState data = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
                data.setFacing(face);
                place.set(face, data);
                break;
            }
        }
    }, PacketItemTypes.COCOA_BEANS),

    DIRT_PATH((player, place) -> {
        PacketBlockState state = place.getDirectionalState(BlockFace.UP);
        // If there is a solid block above the dirt path, it turns to air.  This does not include fence gates
        if (!state.getType().isBlocking() || BlockTags.FENCE_GATES.contains(state.getType())) {
            place.set(place.getMaterial());
        } else {
            place.set(PacketStateTypes.DIRT);
        }
    }, PacketItemTypes.DIRT_PATH),

    HOPPER((player, place) -> {
        BlockFace opposite = place.getDirection().getOppositeFace();
        PacketBlockState hopper = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
        hopper.setFacing(place.isFaceVertical() ? BlockFace.DOWN : opposite);
        place.set(hopper);
    }, PacketItemTypes.HOPPER),

    LANTERN((player, place) -> {
        for (BlockFace face : place.getNearestPlacingDirections()) {
            if (BlockFaceHelper.isFaceHorizontal(face)) continue;
            PacketBlockState lantern = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);

            boolean isHanging = face == BlockFace.UP;
            lantern.setHanging(isHanging);

            boolean canSurvive = place.isFaceFullCenter(isHanging ? BlockFace.UP : BlockFace.DOWN) && !BlockTags.FENCE_GATES.contains(place.getPlacedAgainstMaterial());
            if (!canSurvive) continue;

            place.set(lantern);
            return;
        }
    }, PacketItemTypes.LANTERN, PacketItemTypes.SOUL_LANTERN),

    POINTED_DRIPSTONE((player, place) -> {
        // To explain what Mojang is doing, take the example of placing on top face
        BlockFace primaryDir = place.getNearestVerticalDirection().getOppositeFace(); // The player clicked downwards, so use upwards
        PacketBlockState typePlacingOn = place.getDirectionalState(primaryDir.getOppositeFace()); // Block we are placing on

        // Check to see if we can place on the block or there is dripstone on the block that we are placing on also pointing upwards
        boolean primarySameType = typePlacingOn.getInternalData().containsKey(StateValue.VERTICAL_DIRECTION) && typePlacingOn.getVerticalDirection().name().equals(primaryDir.name());
        boolean primaryValid = place.isFullFace(primaryDir.getOppositeFace()) || primarySameType;

        // Try to use the opposite direction, just to see if switching directions makes it valid.
        if (!primaryValid) {
            BlockFace secondaryDirection = primaryDir.getOppositeFace(); // See if placing it DOWNWARDS is valid
            PacketBlockState secondaryType = place.getDirectionalState(secondaryDirection.getOppositeFace()); // Get the block above us
            // Check if the dripstone above us is also facing downwards
            boolean secondarySameType = secondaryType.getInternalData().containsKey(StateValue.VERTICAL_DIRECTION) && secondaryType.getVerticalDirection().name().equals(primaryDir.name());

            primaryDir = secondaryDirection;
            // Update block survivability
            primaryValid = place.isFullFace(secondaryDirection.getOppositeFace()) || secondarySameType;
        }

        // No valid locations
        if (!primaryValid) return;

        PacketBlockState toPlace = PacketStateTypes.POINTED_DRIPSTONE.createBlockState(CompensatedWorld.blockVersion);
        toPlace.setVerticalDirection(VerticalDirection.valueOf(primaryDir.name())); // This block is facing UPWARDS as placed on the top face

        // We then have to calculate the thickness of the dripstone
        //
        // PrimaryDirection should be the direction that the current dripstone being placed will face
        // oppositeType should be the opposite to the direction the dripstone is facing, what it is pointing into
        //
        // If the dripstone is -> <- pointed at one another

        // If check the blockstate that is above now with the direction of DOWN
        Vector3i placedPos = place.getPlacedBlockPos();
        Dripstone.update(player, toPlace, placedPos.getX(), placedPos.getY(), placedPos.getZ(), place.isSecondaryUse());

        place.set(toPlace);
    }, PacketItemTypes.POINTED_DRIPSTONE),

    CACTUS((player, place) -> {
        for (BlockFace face : BlockPlace.getHorizontalFaces()) {
            if (place.isSolidBlocking(face) || place.isLava(face)) {
                return;
            }
        }

        if (place.isOn(PacketStateTypes.CACTUS, PacketStateTypes.SAND, PacketStateTypes.RED_SAND) && !place.isLava(BlockFace.UP)) {
            place.set();
        }
    }, PacketItemTypes.CACTUS),

    CAKE((player, place) -> {
        if (place.isSolidBlocking(BlockFace.DOWN)) {
            place.set();
        }
    }, PacketItemTypes.CAKE),

    CANDLE_CAKE((player, place) -> {
        if (place.isSolidBlocking(BlockFace.DOWN)) {
            place.set();
        }
    }, PacketItemTypes.values().stream().filter(mat -> mat.getName().getKey().contains("candle_cake"))
            .toList().toArray(new ItemType[0])),

    PISTON_BASE((player, place) -> {
        PacketBlockState piston = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
        piston.setFacing(place.getNearestVerticalDirection().getOppositeFace());
        place.set(piston);
    }, PacketItemTypes.PISTON, PacketItemTypes.STICKY_PISTON),

    AZALEA((player, place) -> {
        PacketBlockState below = place.getBelowState();
        if (place.isOnDirt() || below.getType() == PacketStateTypes.FARMLAND || below.getType() == PacketStateTypes.CLAY) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.AZALEA, PacketItemTypes.FLOWERING_AZALEA),

    CROP((player, place) -> {
        PacketBlockState below = place.getBelowState();
        if (below.getType() == PacketStateTypes.FARMLAND) {
            // This is wrong and depends on lighting, but the server resync's anyways plus this isn't a solid block so I don't care.
            place.set();
        }
    }, PacketItemTypes.CARROT, PacketItemTypes.BEETROOT, PacketItemTypes.POTATO,
            PacketItemTypes.PUMPKIN_SEEDS, PacketItemTypes.MELON_SEEDS, PacketItemTypes.WHEAT_SEEDS, PacketItemTypes.TORCHFLOWER_SEEDS),

    SUGARCANE((player, place) -> {
        if (place.isOn(PacketStateTypes.SUGAR_CANE)) {
            place.set();
            return;
        }

        if (place.isOnDirt() || place.isOn(PacketStateTypes.SAND, PacketStateTypes.RED_SAND)) {
            Vector3i pos = place.getPlacedBlockPos();
            pos = pos.withY(pos.getY() - 1);

            for (BlockFace direction : BlockPlace.getHorizontalFaces()) {
                Vector3i toSearchPos = pos;
                toSearchPos = toSearchPos.withX(toSearchPos.getX() + direction.getModX());
                toSearchPos = toSearchPos.withZ(toSearchPos.getZ() + direction.getModZ());

                PacketBlockState directional = player.compensatedWorld.getBlock(toSearchPos);
                if (Materials.isWater(player.getClientVersion(), directional) || directional.getType() == PacketStateTypes.FROSTED_ICE) {
                    place.set();
                    return;
                }
            }
        }
    }, PacketItemTypes.SUGAR_CANE),

    CARPET((player, place) -> {
        if (!place.getBelowMaterial().isAir()) {
            place.set();
        }
    }, ItemTags.WOOL_CARPETS),

    // Moss carpet is a carpet not under the carpets tag
    MOSS_CARPET(CARPET.data, PacketItemTypes.MOSS_CARPET, PacketItemTypes.PALE_MOSS_CARPET),

    CHORUS_FLOWER((player, place) -> {
        PacketBlockState blockstate = place.getBelowState();
        if (blockstate.getType() != PacketStateTypes.CHORUS_PLANT && blockstate.getType() != PacketStateTypes.END_STONE) {
            if (blockstate.getType().isAir()) {
                boolean flag = false;

                for (BlockFace direction : BlockPlace.getHorizontalFaces()) {
                    PacketBlockState blockstate1 = place.getDirectionalState(direction);
                    if (blockstate1.getType() == PacketStateTypes.CHORUS_PLANT) {
                        if (flag) {
                            return;
                        }

                        flag = true;
                    } else if (!blockstate.getType().isAir()) {
                        return;
                    }
                }

                if (flag) {
                    place.set();
                }
            }
        } else {
            place.set();
        }
    }, PacketItemTypes.CHORUS_FLOWER),

    CHORUS_PLANT((player, place) -> {
        PacketBlockState blockstate = place.getBelowState();
        boolean flag = !place.getAboveState().getType().isAir() && !blockstate.getType().isAir();

        for (BlockFace direction : BlockPlace.getHorizontalFaces()) {
            PacketBlockState blockstate1 = place.getDirectionalState(direction);
            if (blockstate1.getType() == PacketStateTypes.CHORUS_PLANT) {
                if (flag) {
                    return;
                }

                Vector3i placedPos = place.getPlacedBlockPos();
                placedPos = placedPos.add(direction.getModX(), -1, direction.getModZ());

                PacketBlockState blockstate2 = player.compensatedWorld.getBlock(placedPos);
                if (blockstate2.getType() == PacketStateTypes.CHORUS_PLANT || blockstate2.getType() == PacketStateTypes.END_STONE) {
                    place.set();
                }
            }
        }

        if (blockstate.getType() == PacketStateTypes.CHORUS_PLANT || blockstate.getType() == PacketStateTypes.END_STONE) {
            place.set();
        }
    }, PacketItemTypes.CHORUS_PLANT),

    DEAD_BUSH((player, place) -> {
        PacketBlockState below = place.getBelowState();
        if (below.getType() == PacketStateTypes.SAND || below.getType() == PacketStateTypes.RED_SAND ||
                BlockTags.TERRACOTTA.contains(below.getType()) || place.isOnDirt()) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.DEAD_BUSH),

    DIODE((player, place) -> {
        if (place.isFaceRigid(BlockFace.DOWN)) {
            place.set();
        }
    }, PacketItemTypes.REPEATER, PacketItemTypes.COMPARATOR, PacketItemTypes.REDSTONE),

    FUNGUS((player, place) -> {
        if (place.isOn(PacketStateTypes.CRIMSON_NYLIUM, PacketStateTypes.WARPED_NYLIUM, PacketStateTypes.MYCELIUM, PacketStateTypes.SOUL_SOIL, PacketStateTypes.FARMLAND) || place.isOnDirt()) {
            place.set();
        }
    }, PacketItemTypes.CRIMSON_FUNGUS, PacketItemTypes.WARPED_FUNGUS),

    SPROUTS((player, place) -> {
        if (place.isOn(PacketStateTypes.CRIMSON_NYLIUM, PacketStateTypes.WARPED_NYLIUM, PacketStateTypes.SOUL_SOIL, PacketStateTypes.FARMLAND) || place.isOnDirt()) {
            place.set();
        }
    }, PacketItemTypes.NETHER_SPROUTS, PacketItemTypes.WARPED_ROOTS, PacketItemTypes.CRIMSON_ROOTS),

    NETHER_WART((player, place) -> {
        if (place.isOn(PacketStateTypes.SOUL_SAND)) {
            place.set();
        }
    }, PacketItemTypes.NETHER_WART),

    WATERLILY((player, place) -> {
        PacketBlockState below = place.getDirectionalState(BlockFace.DOWN);
        if (!place.isInLiquid() && (Materials.isWater(player.getClientVersion(), below) || place.isOn(PacketStateTypes.ICE, PacketStateTypes.FROSTED_ICE))) {
            place.set();
        }
    }, PacketItemTypes.LILY_PAD),

    WITHER_ROSE((player, place) -> {
        if (place.isOn(PacketStateTypes.NETHERRACK, PacketStateTypes.SOUL_SAND, PacketStateTypes.SOUL_SOIL, PacketStateTypes.FARMLAND) || place.isOnDirt()) {
            place.set();
        }
    }, PacketItemTypes.WITHER_ROSE),

    // Blocks that have both wall and standing states
    TORCH_OR_HEAD((player, place) -> {
        // type doesn't matter to grim, same hitbox.
        // If it's a torch, create a wall torch
        // Otherwise, it's going to be a head.  The type of this head also doesn't matter
        PacketBlockState dir;
        boolean isTorch = place.getMaterial().getName().contains("torch");
        boolean isHead = place.getMaterial().getName().contains("head") || place.getMaterial().getName().contains("skull");
        boolean isWallSign = !isTorch && !isHead;

        if (isHead && player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_12_2))
            return; // 1.12- players don't predict head places

        if (isTorch) {
            dir = PacketStateTypes.WALL_TORCH.createBlockState(CompensatedWorld.blockVersion);
        } else if (place.getMaterial().getName().contains("head") || place.getMaterial().getName().contains("skull")) {
            dir = PacketStateTypes.PLAYER_WALL_HEAD.createBlockState(CompensatedWorld.blockVersion);
        } else {
            dir = PacketStateTypes.OAK_WALL_SIGN.createBlockState(CompensatedWorld.blockVersion);
        }

        for (BlockFace face : place.getNearestPlacingDirections()) {
            // Torches need solid faces
            // Heads have no special preferences - place them anywhere
            // Signs need solid - exempts chorus flowers and a few other strange cases
            if (face != BlockFace.UP) {
                if (BlockFaceHelper.isFaceHorizontal(face)) {
                    boolean canPlace = isHead || ((isWallSign || place.isFullFace(face)) && (isTorch || place.isSolidBlocking(face)));
                    if (canPlace && face != BlockFace.UP) { // center requires nothing (head), full face (torch), or solid (sign)
                        dir.setFacing(face.getOppositeFace());
                        place.set(dir);
                        return;
                    }
                } else {
                    boolean canPlace = isHead || ((isWallSign || place.isFaceFullCenter(face)) && (isTorch || place.isSolidBlocking(face)));
                    if (canPlace) {
                        place.set(place.getMaterial());
                        return;
                    }
                }
            }
        }
    }, PacketItemTypes.values().stream().filter(mat ->
                    mat.getName().getKey().contains("torch") // Find all torches
                            || (mat.getName().getKey().contains("head") || mat.getName().getKey().contains("skull")) && !mat.getName().getKey().contains("piston") // Skulls
                            || mat.getName().getKey().contains("sign")) // And signs
            .toArray(ItemType[]::new)),

    MULTI_FACE_BLOCK((player, place) -> {
        PacketStateType placedType = place.getMaterial();

        PacketBlockState multiFace = place.getExistingBlockData();
        if (multiFace.getType() != placedType) {
            multiFace = BlockPlace.createBlockState(placedType, CompensatedWorld.blockVersion);
        }

        for (BlockFace face : place.getNearestPlacingDirections()) {
            switch (face) {
                case UP:
                    if (multiFace.isUp()) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setUp(true);
                        break;
                    }
                    continue;
                case DOWN:
                    if (multiFace.isDown()) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setDown(true);
                        break;
                    }
                    continue;
                case NORTH:
                    if (multiFace.getNorth() == North.TRUE) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setNorth(North.TRUE);
                        break;
                    }
                    continue;
                case SOUTH:
                    if (multiFace.getSouth() == South.TRUE) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setSouth(South.TRUE);
                        break;
                    }
                    continue;
                case EAST:
                    if (multiFace.getEast() == East.TRUE) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setEast(East.TRUE);
                        return;
                    }
                    continue;
                case WEST:
                    if (multiFace.getWest() == West.TRUE) continue;
                    if (place.isFullFace(face)) {
                        multiFace.setWest(West.TRUE);
                        break;
                    }
            }
        }

        place.set(multiFace);
    }, PacketItemTypes.GLOW_LICHEN, PacketItemTypes.SCULK_VEIN),

    FACE_ATTACHED_HORIZONTAL_DIRECTIONAL((player, place) -> {
        for (BlockFace face : place.getNearestPlacingDirections()) {
            if (place.isFullFace(face)) {
                place.set(place.getMaterial());
                return;
            }
        }
    }, PacketItemTypes.values().stream().filter(mat -> mat.getName().getKey().contains("button") // Find all buttons
                    || mat.getName().getKey().contains("lever")) // And levers
            .toArray(ItemType[]::new)),

    GRINDSTONE((player, place) -> { // Grindstones do not have special survivability requirements
        PacketBlockState stone = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
        if (place.isFaceVertical()) {
            stone.setFace(place.getPlayerFacing() == BlockFace.UP ? Face.CEILING : Face.FLOOR);
        } else {
            stone.setFace(Face.WALL);
        }
        stone.setFacing(place.getPlayerFacing());
        place.set(stone);
    }, PacketItemTypes.GRINDSTONE),

    // Blocks that have both wall and standing states
    // Banners
    BANNER((player, place) -> {
        for (BlockFace face : place.getNearestPlacingDirections()) {
            if (place.isSolidBlocking(face) && face != BlockFace.UP) {
                if (BlockFaceHelper.isFaceHorizontal(face)) {
                    // type doesn't matter to grim, same hitbox.
                    // If it's a torch, create a wall torch
                    // Otherwise, it's going to be a head.  The type of this head also doesn't matter.
                    PacketBlockState dir = PacketStateTypes.BLACK_WALL_BANNER.createBlockState(CompensatedWorld.blockVersion);
                    dir.setFacing(face.getOppositeFace());
                    place.set(dir);
                } else {
                    place.set(place.getMaterial());
                }
                break;
            }
        }
    }, ItemTags.BANNERS),

    BIG_DRIPLEAF((player, place) -> {
        PacketBlockState existing = place.getDirectionalState(BlockFace.DOWN);
        if (place.isFullFace(BlockFace.DOWN) || existing.getType() == PacketStateTypes.BIG_DRIPLEAF || existing.getType() == PacketStateTypes.BIG_DRIPLEAF_STEM) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.BIG_DRIPLEAF),

    SMALL_DRIPLEAF((player, place) -> {
        PacketBlockState existing = place.getDirectionalState(BlockFace.DOWN);
        if (place.isBlockFaceOpen(BlockFace.UP) && BlockTags.SMALL_DRIPLEAF_PLACEABLE.contains(existing.getType()) || (place.isInWater() && (place.isOnDirt() || existing.getType() == PacketStateTypes.FARMLAND))) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.SMALL_DRIPLEAF),

    SEAGRASS((player, place) -> {
        PacketBlockState existing = place.getDirectionalState(BlockFace.DOWN);
        if (place.isInWater() && place.isFullFace(BlockFace.DOWN) && existing.getType() != PacketStateTypes.MAGMA_BLOCK) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.SEAGRASS),

    HANGING_ROOT((player, place) -> {
        if (place.isFullFace(BlockFace.UP)) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.HANGING_ROOTS),

    SPORE_BLOSSOM((player, place) -> {
        if (place.isFullFace(BlockFace.UP) && !place.isInWater()) {
            place.set();
        }
    }, PacketItemTypes.SPORE_BLOSSOM),

    FIRE((player, place) -> {
        boolean byFlammable = false;
        for (BlockFace face : BlockFace.values()) {
            // Do we care about this enuogh to fix? // TODO: Check flmmable
            byFlammable = true;
        }

        if (byFlammable || place.isFullFace(BlockFace.DOWN)) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.FLINT_AND_STEEL, PacketItemTypes.FIRE_CHARGE), // soul fire isn't directly placeable

    TRIPWIRE_HOOK((player, place) -> {
        if (place.isFaceHorizontal() && place.isFullFace(place.getDirection().getOppositeFace())) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.TRIPWIRE_HOOK),

    CORAL_PLANT((player, place) -> {
        if (place.isFullFace(BlockFace.DOWN)) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.values().stream().filter(mat -> (mat.getName().getKey().contains("coral")
                    && !mat.getName().getKey().contains("block") && !mat.getName().getKey().contains("fan")))
            .toArray(ItemType[]::new)),

    CORAL_FAN((player, place) -> {
        for (BlockFace face : place.getNearestPlacingDirections()) {
            // Torches need solid faces
            // Heads have no special preferences - place them anywhere
            // Signs need solid - exempts chorus flowers and a few other strange cases
            if (face != BlockFace.UP) {
                boolean canPlace = place.isFullFace(face);
                if (BlockFaceHelper.isFaceHorizontal(face)) {
                    if (canPlace) { // center requires nothing (head), full face (torch), or solid (sign)
                        PacketBlockState coralFan = PacketStateTypes.FIRE_CORAL_WALL_FAN.createBlockState(CompensatedWorld.blockVersion);
                        coralFan.setFacing(face);
                        place.set(coralFan);
                        return;
                    }
                } else if (place.isFaceFullCenter(BlockFace.DOWN) && canPlace) {
                    place.set(place.getMaterial());
                    return;
                }
            }
        }
    }, PacketItemTypes.values().stream().filter(mat -> (mat.getName().getKey().contains("coral")
                    && !mat.getName().getKey().contains("block") && mat.getName().getKey().contains("fan")))
            .toArray(ItemType[]::new)),

    PRESSURE_PLATE((player, place) -> {
        if (place.isFullFace(BlockFace.DOWN) || place.isFaceFullCenter(BlockFace.DOWN)) {
            place.set();
        }
    }, PacketItemTypes.values().stream().filter(mat -> (mat.getName().getKey().contains("plate")))
            .toArray(ItemType[]::new)),

    RAIL((player, place) -> {
        if (place.isFaceRigid(BlockFace.DOWN)) {
            place.set(place.getMaterial());
        }
    }, ItemTags.RAILS),

    KELP((player, place) -> {
        PacketStateType below = place.getDirectionalState(BlockFace.DOWN).getType();
        PacketBlockState existing = place.getExistingBlockData();

        double fluidLevel = 0;
        if (Materials.isWater(player.getClientVersion(), existing)) {
            if (existing.getType() == PacketStateTypes.WATER) {
                int level = existing.getLevel();
                // Falling water has a level of 8
                fluidLevel = ((level & 0x8) == 8) ? (8.0 / 9.0f) : (8 - level) / 9.0f;
            } else { // Water source block such as bubble columns
                fluidLevel = 1.0;
            }
        }

        if (below != PacketStateTypes.MAGMA_BLOCK && (place.isFullFace(BlockFace.DOWN) || below == PacketStateTypes.KELP || below == PacketStateTypes.KELP_PLANT) && fluidLevel >= 8 / 9d) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.KELP),

    CAVE_VINE((player, place) -> {
        PacketStateType below = place.getDirectionalState(BlockFace.UP).getType();
        if (place.isFullFace(BlockFace.DOWN) || below == PacketStateTypes.CAVE_VINES || below == PacketStateTypes.CAVE_VINES_PLANT) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.GLOW_BERRIES),

    WEEPING_VINE((player, place) -> {
        PacketStateType below = place.getDirectionalState(BlockFace.UP).getType();
        if (place.isFullFace(BlockFace.UP) || below == PacketStateTypes.WEEPING_VINES || below == PacketStateTypes.WEEPING_VINES_PLANT) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.WEEPING_VINES),

    TWISTED_VINE((player, place) -> {
        PacketStateType below = place.getDirectionalState(BlockFace.DOWN).getType();
        if (place.isFullFace(BlockFace.DOWN) || below == PacketStateTypes.TWISTING_VINES || below == PacketStateTypes.TWISTING_VINES_PLANT) {
            place.set(place.getMaterial());
        }
    }, PacketItemTypes.TWISTING_VINES),

    // Vine logic
    // If facing up, then there is a face facing up.
    // Checks for solid faces in the direction that it is in
    // Also checks for vines with the same directional above itself
    // However, as all vines have the same hitbox (to collisions and climbing)
    // As long as one of these properties is met, it is good enough for grim!
    VINE((player, place) -> {
        if (place.getAboveState().getType() == PacketStateTypes.VINE) {
            place.set();
            return;
        }

        for (BlockFace face : BlockPlace.getHorizontalFaces()) {
            if (place.isSolidBlocking(face)) {
                place.set();
                return;
            }
        }
    }, PacketItemTypes.VINE),

    LECTERN((player, place) -> {
        PacketBlockState lectern = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
        lectern.setFacing(place.getPlayerFacing().getOppositeFace());
        place.set(lectern);
    }, PacketItemTypes.LECTERN),

    FENCE_GATE((player, place) -> {
        PacketBlockState gate = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
        gate.setFacing(place.getPlayerFacing());

        // Check for redstone signal!
        if (place.isBlockPlacedPowered()) {
            gate.setOpen(true);
        }

        place.set(gate);
    }, BlockTags.FENCE_GATES),

    TRAPDOOR((player, place) -> {
        PacketBlockState door = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);

        BlockFace direction = place.getDirection();
        if (!place.isReplaceClicked() && BlockFaceHelper.isFaceHorizontal(direction)) {
            door.setFacing(direction);
            boolean clickedTop = place.getClickedLocation().getY() > 0.5;
            Half half = clickedTop ? Half.TOP : Half.BOTTOM;
            door.setHalf(half);
        } else if (player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9)) { // 1.9 logic only
            door.setFacing(place.getPlayerFacing().getOppositeFace());
            Half half = direction == BlockFace.UP ? Half.BOTTOM : Half.TOP;
            door.setHalf(half);
        }

        // Check for redstone signal!
        if (place.isBlockPlacedPowered()) {
            door.setOpen(true);
        }

        // 1.8 has special placing requirements
        if (player.getClientVersion().isOlderThan(PacketClientVersions.V_1_9)) {
            PacketBlockState dirState = place.getDirectionalState(door.getFacing().getOppositeFace());
            boolean fullFace = CollisionData.getData(dirState.getType()).getMovementCollisionBox(player, player.getClientVersion(), dirState).isFullBlock();
            boolean blacklisted = BlockTags.ICE.contains(dirState.getType()) || BlockTags.GLASS_BLOCKS.contains(dirState.getType()) ||
                    dirState.getType() == PacketStateTypes.TNT || BlockTags.LEAVES.contains(dirState.getType()) ||
                    dirState.getType() == PacketStateTypes.SNOW || dirState.getType() == PacketStateTypes.CACTUS;
            boolean whitelisted = dirState.getType() == PacketStateTypes.GLOWSTONE || BlockTags.SLABS.contains(dirState.getType()) ||
                    BlockTags.STAIRS.contains(dirState.getType());

            // Need a solid block to place a trapdoor on
            if (!((dirState.getType().isBlocking() && !blacklisted && fullFace) || whitelisted)) {
                return;
            }
        }


        place.set(door);
    }, ItemTags.TRAPDOORS),

    DOOR((player, place) -> {
        if (place.isFullFace(BlockFace.DOWN) && place.isBlockFaceOpen(BlockFace.UP)) {
            PacketBlockState door = BlockPlace.createBlockState(place.getMaterial(), CompensatedWorld.blockVersion);
            door.setFacing(place.getPlayerFacing());

            // Get the hinge
            BlockFace playerFacing = place.getPlayerFacing();

            BlockFace ccw = BlockFaceHelper.getCounterClockwise(playerFacing);
            PacketBlockState ccwState = place.getDirectionalState(ccw);
            CollisionBox ccwBox = CollisionData.getData(ccwState.getType()).getMovementCollisionBox(player, player.getClientVersion(), ccwState);

            Vector3dm aboveCCWPos = place.getClickedLocation().add(new Vector3dm(ccw.getModX(), ccw.getModY(), ccw.getModZ())).add(new Vector3dm(0, 1, 0));
            PacketBlockState aboveCCWState = player.compensatedWorld.getBlock(aboveCCWPos);
            CollisionBox aboveCCWBox = CollisionData.getData(aboveCCWState.getType()).getMovementCollisionBox(player, player.getClientVersion(), aboveCCWState);

            BlockFace cw = BlockFaceHelper.getPEClockWise(playerFacing);
            PacketBlockState cwState = place.getDirectionalState(cw);
            CollisionBox cwBox = CollisionData.getData(cwState.getType()).getMovementCollisionBox(player, player.getClientVersion(), cwState);

            Vector3dm aboveCWPos = place.getClickedLocation().add(new Vector3dm(cw.getModX(), cw.getModY(), cw.getModZ())).add(new Vector3dm(0, 1, 0));
            PacketBlockState aboveCWState = player.compensatedWorld.getBlock(aboveCWPos);
            CollisionBox aboveCWBox = CollisionData.getData(aboveCWState.getType()).getMovementCollisionBox(player, player.getClientVersion(), aboveCWState);

            int i = (ccwBox.isFullBlock() ? -1 : 0) + (aboveCCWBox.isFullBlock() ? -1 : 0) + (cwBox.isFullBlock() ? 1 : 0) + (aboveCWBox.isFullBlock() ? 1 : 0);

            boolean isCCWLower = false;
            if (BlockTags.DOORS.contains(ccwState.getType()))
                isCCWLower = ccwState.getHalf() == Half.LOWER;

            boolean isCWLower = false;
            if (BlockTags.DOORS.contains(cwState.getType()))
                isCWLower = ccwState.getHalf() == Half.LOWER;

            Hinge hinge;
            if ((!isCCWLower || isCWLower) && i <= 0) {
                if ((!isCWLower || isCCWLower) && i >= 0) {
                    int j = playerFacing.getModX();
                    int k = playerFacing.getModZ();
                    Vector3dm vec3 = place.getClickedLocation();
                    double d0 = vec3.getX();
                    double d1 = vec3.getY();
                    hinge = (j >= 0 || d1 >= 0.5D) && (j <= 0 || d1 <= 0.5D) && (k >= 0 || d0 <= 0.5D) && (k <= 0 || d0 >= 0.5D) ? Hinge.LEFT : Hinge.RIGHT;
                } else {
                    hinge = Hinge.LEFT;
                }
            } else {
                hinge = Hinge.RIGHT;
            }

            // Check for redstone signal!
            if (place.isBlockPlacedPowered()) {
                door.setOpen(true);
            }

            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) { // Only works on 1.13+
                door.setHinge(hinge);
            }

            door.setHalf(Half.LOWER);
            place.set(door);

            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) { // Only works on 1.13+
                door.setHalf(Half.UPPER);
                place.setAbove(door);
            } else {
                // We have to create a new door just for upper... due to neither door having complete info
                // Lol, I have to use strings as PacketEvents wasn't designed around one material having two sets of data
                // This is 1.12 only, but the server is also 1.12
                PacketBlockState above = PacketBlockState.getByString(CompensatedWorld.blockVersion, "minecraft:" + place.getMaterial().getName().toLowerCase(Locale.ROOT) + "[half=upper,hinge=" + hinge.toString().toLowerCase(Locale.ROOT) + "]");
                place.setAbove(above);
            }
        }
    }, ItemTags.DOORS),

    SCAFFOLDING((player, place) -> {
        place.setReplaceClicked(false); // scaffolding is sometimes replace clicked

        // The client lies about block place location and face to not false vanilla ac
        // However, this causes TWO desync's!
        if (place.getPlacedAgainstMaterial() == PacketStateTypes.SCAFFOLDING) {
            // This can desync due to look being a tick behind, pls fix mojang
            // Convert the packet to the real direction
            BlockFace direction;
            if (place.isSecondaryUse()) {
                direction = place.isInside() ? place.getDirection().getOppositeFace() : place.getDirection();
            } else {
                direction = place.getDirection() == BlockFace.UP ? place.getPlayerFacing() : BlockFace.UP;
            }

            place.setFace(direction);

            // Mojang also lies about the location causing another GOD DAMN DESYNC
            // Jesus christ, two desync's in a single block... should I be disappointed or concerned?
            // Ghost blocks won't be fixed because of how it depends on the world state
            int i = 0;
            Vector3i starting = new Vector3i(place.getPlacedAgainstBlockLocation().getX() + direction.getModX(), place.getPlacedAgainstBlockLocation().getY() + direction.getModY(), place.getPlacedAgainstBlockLocation().getZ() + direction.getModZ());
            while (i < 7) {
                if (player.compensatedWorld.getBlock(starting).getType() != PacketStateTypes.SCAFFOLDING) {
                    if (player.compensatedWorld.getBlock(starting).getType().isReplaceable()) {
                        place.setBlockPosition(starting);
                        place.setReplaceClicked(true);
                        break; // We found it!
                    }
                    return; // Cancel block place
                }

                starting = new Vector3i(starting.getX() + direction.getModX(), starting.getY() + direction.getModY(), starting.getZ() + direction.getModZ());
                if (BlockFaceHelper.isFaceHorizontal(direction)) {
                    i++;
                }
            }
            if (i == 7) return; // Cancel block place
        } // else, cancel if the scaffolding is exactly 7 away, grim doesn't handle this edge case yet.


        // A scaffolding has a distance of 0 IFF it is placed above a sturdy face
        // Else it has a distance greater than 0
        boolean sturdyBelow = place.isFullFace(BlockFace.DOWN);
        boolean isBelowScaffolding = place.getBelowMaterial() == PacketStateTypes.SCAFFOLDING;
        boolean isBottom = !sturdyBelow && !isBelowScaffolding;

        PacketBlockState scaffolding = PacketStateTypes.SCAFFOLDING.createBlockState(CompensatedWorld.blockVersion);
        scaffolding.setBottom(isBottom);

        place.set(scaffolding);
    }, PacketItemTypes.SCAFFOLDING),

    DOUBLE_PLANT((player, place) -> {
        if (place.isBlockFaceOpen(BlockFace.UP) && place.isOnDirt() || place.isOn(PacketStateTypes.FARMLAND)) {
            place.set();
            place.setAbove(); // Client predicts block above
        }
    }, PacketItemTypes.TALL_GRASS, PacketItemTypes.LARGE_FERN, PacketItemTypes.SUNFLOWER,
            PacketItemTypes.LILAC, PacketItemTypes.ROSE_BUSH, PacketItemTypes.PEONY),

    MUSHROOM((player, place) -> {
        if (BlockTags.MUSHROOM_GROW_BLOCK.contains(place.getBelowMaterial())) {
            place.set();
        } else if (place.isFullFace(BlockFace.DOWN)) { // TODO: Check occluding
            Vector3i placedPos = place.getPlacedBlockPos();
            // This is wrong and depends on lighting, but the server resync's anyways plus this isn't a solid block. so I don't care.
            place.set();
        }
    }, PacketItemTypes.BROWN_MUSHROOM, PacketItemTypes.RED_MUSHROOM),

    MANGROVE_PROPAGULE((player, place) -> {
        // Must be hanging below mangrove leaves
        if (place.getAboveState().getType() != PacketStateTypes.MANGROVE_LEAVES) return;
        // Fall back to BUSH_BLOCK_TYPE
        if (place.isOnDirt() || place.isOn(PacketStateTypes.FARMLAND)) {
            place.set();
        }
    }, PacketItemTypes.MANGROVE_PROPAGULE),

    FROGSPAWN((player, place) -> {
        if (Materials.isWater(player.getClientVersion(), place.getExistingBlockData()) && Materials.isWater(player.getClientVersion(), place.getAboveState())) {
            place.set();
        }
    }, PacketItemTypes.FROGSPAWN),

    BUSH_BLOCK_TYPE((player, place) -> {
        if (place.isOnDirt() || place.isOn(PacketStateTypes.FARMLAND)) {
            place.set();
        }
    }, PacketItemTypes.SPRUCE_SAPLING, PacketItemTypes.ACACIA_SAPLING,
            PacketItemTypes.BIRCH_SAPLING, PacketItemTypes.DARK_OAK_SAPLING,
            PacketItemTypes.OAK_SAPLING, PacketItemTypes.JUNGLE_SAPLING,
            PacketItemTypes.SWEET_BERRIES, PacketItemTypes.DANDELION,
            PacketItemTypes.POPPY, PacketItemTypes.BLUE_ORCHID,
            PacketItemTypes.ALLIUM, PacketItemTypes.AZURE_BLUET,
            PacketItemTypes.RED_TULIP, PacketItemTypes.ORANGE_TULIP,
            PacketItemTypes.WHITE_TULIP, PacketItemTypes.PINK_TULIP,
            PacketItemTypes.OXEYE_DAISY, PacketItemTypes.CORNFLOWER,
            PacketItemTypes.LILY_OF_THE_VALLEY, PacketItemTypes.PINK_PETALS,
            PacketItemTypes.SHORT_GRASS),

    POWDER_SNOW_BUCKET((player, place) -> {
        place.set();
        CheckManagerListener.setPlayerItem(player, place.getHand(), PacketItemTypes.BUCKET);
    }, PacketItemTypes.POWDER_SNOW_BUCKET),

    GAME_MASTER((player, place) -> {
        if (player.canUseGameMasterBlocks()) {
            place.set();
        }
    }, PacketItemTypes.COMMAND_BLOCK, PacketItemTypes.CHAIN_COMMAND_BLOCK, PacketItemTypes.REPEATING_COMMAND_BLOCK,
            PacketItemTypes.JIGSAW, PacketItemTypes.STRUCTURE_BLOCK),

    NO_DATA((player, place) -> place.set(place.getMaterial()), PacketItemTypes.AIR);

    // This should be an array... but a hashmap will do for now...
    private static final Map<PacketItemType, BlockPlaceResult> lookupMap = new HashMap<>();

    static {
        for (BlockPlaceResult data : values()) {
            for (PacketItemType type : data.materials) {
                lookupMap.put(type, data);
            }
        }
    }

    private final BlockPlaceFactory data;
    private final PacketItemType[] materials;

    BlockPlaceResult(BlockPlaceFactory data, PacketItemType... materials) {
        this.data = data;
        Set<PacketItemType> mList = new HashSet<>(Arrays.asList(materials));
        mList.remove(null); // Sets can contain one null
        this.materials = mList.toArray(new PacketItemType[0]);
    }

    BlockPlaceResult(BlockPlaceFactory data, ItemTags tags) {
        this(data, tags.getStates().toArray(new PacketItemType[0]));
    }

    BlockPlaceResult(BlockPlaceFactory data, BlockTags tag) {
        List<PacketItemType> types = new ArrayList<>(tag.getStates().size());
        for (PacketStateType state : tag.getStates()) {
            types.add(state.getTypePlacingState());
        }

        this.data = data;
        this.materials = types.toArray(new PacketItemType[0]);
    }

    public static BlockPlaceFactory getMaterialData(PacketItemType placed) {
        return lookupMap.getOrDefault(placed, NO_DATA).data;
    }
}
