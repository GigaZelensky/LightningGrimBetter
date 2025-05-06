package ac.grim.grimac.utils.nmsutil;

import ac.grim.grimac.api.packet.item.PacketItemAttribute;
import ac.grim.grimac.api.packet.item.PacketItemType;
import ac.grim.grimac.api.packet.item.PacketItemTypes;
import ac.grim.grimac.api.packet.item.PacketStateType;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Materials {
    private static final Set<PacketStateType> NO_PLACE_LIQUIDS = new HashSet<>();
    // Includes iron panes in addition to glass panes
    private static final Set<PacketStateType> PANES = new HashSet<>();
    private static final Set<PacketStateType> WATER_LIQUIDS = new HashSet<>();
    private static final Set<PacketStateType> WATER_LIQUIDS_LEGACY = new HashSet<>();
    private static final Set<PacketStateType> WATER_SOURCES = new HashSet<>();
    private static final Set<PacketStateType> WATER_SOURCES_LEGACY = new HashSet<>();

    private static final Set<PacketStateType> COPPER_DOORS = new HashSet<>();
    private static final Set<PacketStateType> COPPER_TRAPDOORS = new HashSet<>();

    private static final Set<PacketStateType> CLIENT_SIDE = new HashSet<>();

    static {
        // Base water, flowing on 1.12- but not on 1.13+ servers
        WATER_LIQUIDS.add(StateTypes.WATER);
        WATER_LIQUIDS_LEGACY.add(StateTypes.WATER);

        // Becomes grass for legacy versions
        WATER_LIQUIDS.add(StateTypes.KELP);
        WATER_SOURCES.add(StateTypes.KELP);
        WATER_LIQUIDS.add(StateTypes.KELP_PLANT);
        WATER_SOURCES.add(StateTypes.KELP_PLANT);

        // Is translated to air for legacy versions
        WATER_SOURCES.add(StateTypes.BUBBLE_COLUMN);
        WATER_LIQUIDS_LEGACY.add(StateTypes.BUBBLE_COLUMN);
        WATER_LIQUIDS.add(StateTypes.BUBBLE_COLUMN);
        WATER_SOURCES_LEGACY.add(StateTypes.BUBBLE_COLUMN);

        // This is not water on 1.12- players
        WATER_SOURCES.add(StateTypes.SEAGRASS);
        WATER_LIQUIDS.add(StateTypes.SEAGRASS);

        // This is not water on 1.12- players`
        WATER_SOURCES.add(StateTypes.TALL_SEAGRASS);
        WATER_LIQUIDS.add(StateTypes.TALL_SEAGRASS);

        NO_PLACE_LIQUIDS.add(StateTypes.WATER);
        NO_PLACE_LIQUIDS.add(StateTypes.LAVA);

        COPPER_DOORS.add(StateTypes.COPPER_DOOR);
        COPPER_DOORS.add(StateTypes.EXPOSED_COPPER_DOOR);
        COPPER_DOORS.add(StateTypes.WEATHERED_COPPER_DOOR);
        COPPER_DOORS.add(StateTypes.OXIDIZED_COPPER_DOOR);
        COPPER_DOORS.add(StateTypes.WAXED_COPPER_DOOR);
        COPPER_DOORS.add(StateTypes.WAXED_EXPOSED_COPPER_DOOR);
        COPPER_DOORS.add(StateTypes.WAXED_WEATHERED_COPPER_DOOR);
        COPPER_DOORS.add(StateTypes.WAXED_OXIDIZED_COPPER_DOOR);

        COPPER_TRAPDOORS.add(StateTypes.COPPER_TRAPDOOR);
        COPPER_TRAPDOORS.add(StateTypes.EXPOSED_COPPER_TRAPDOOR);
        COPPER_TRAPDOORS.add(StateTypes.WEATHERED_COPPER_TRAPDOOR);
        COPPER_TRAPDOORS.add(StateTypes.OXIDIZED_COPPER_TRAPDOOR);
        COPPER_TRAPDOORS.add(StateTypes.WAXED_COPPER_TRAPDOOR);
        COPPER_TRAPDOORS.add(StateTypes.WAXED_EXPOSED_COPPER_TRAPDOOR);
        COPPER_TRAPDOORS.add(StateTypes.WAXED_WEATHERED_COPPER_TRAPDOOR);
        COPPER_TRAPDOORS.add(StateTypes.WAXED_OXIDIZED_COPPER_TRAPDOOR);

        // Important blocks where we need to ignore right-clicking on for placing blocks
        // We can ignore stuff like right-clicking a pumpkin with shears...
        CLIENT_SIDE.add(StateTypes.BARREL);
        CLIENT_SIDE.add(StateTypes.BEACON);
        CLIENT_SIDE.add(StateTypes.BREWING_STAND);
        CLIENT_SIDE.add(StateTypes.CARTOGRAPHY_TABLE);
        CLIENT_SIDE.add(StateTypes.CHEST);
        CLIENT_SIDE.add(StateTypes.TRAPPED_CHEST);
        CLIENT_SIDE.add(StateTypes.COMPARATOR);
        CLIENT_SIDE.add(StateTypes.CRAFTING_TABLE);
        CLIENT_SIDE.add(StateTypes.DAYLIGHT_DETECTOR);
        CLIENT_SIDE.add(StateTypes.DISPENSER);
        CLIENT_SIDE.add(StateTypes.DRAGON_EGG);
        CLIENT_SIDE.add(StateTypes.ENCHANTING_TABLE);
        CLIENT_SIDE.add(StateTypes.ENDER_CHEST);
        CLIENT_SIDE.add(StateTypes.GRINDSTONE);
        CLIENT_SIDE.add(StateTypes.HOPPER);
        CLIENT_SIDE.add(StateTypes.LEVER);
        CLIENT_SIDE.add(StateTypes.LIGHT);
        CLIENT_SIDE.add(StateTypes.LOOM);
        CLIENT_SIDE.add(StateTypes.NOTE_BLOCK);
        CLIENT_SIDE.add(StateTypes.REPEATER);
        CLIENT_SIDE.add(StateTypes.SMITHING_TABLE);
        CLIENT_SIDE.add(StateTypes.STONECUTTER);
        CLIENT_SIDE.add(StateTypes.LECTERN);
        CLIENT_SIDE.add(StateTypes.FURNACE);
        CLIENT_SIDE.add(StateTypes.BLAST_FURNACE);

        CLIENT_SIDE.addAll(BlockTags.FENCE_GATES.getStates());
        CLIENT_SIDE.addAll(BlockTags.ANVIL.getStates());
        CLIENT_SIDE.addAll(BlockTags.BEDS.getStates());
        CLIENT_SIDE.addAll(BlockTags.BUTTONS.getStates());
        CLIENT_SIDE.addAll(BlockTags.SHULKER_BOXES.getStates());
        CLIENT_SIDE.addAll(BlockTags.SIGNS.getStates());
        CLIENT_SIDE.addAll(BlockTags.FLOWER_POTS.getStates());
        CLIENT_SIDE.addAll(BlockTags.TRAPDOORS.getStates().stream().filter(type -> type != StateTypes.IRON_TRAPDOOR).collect(Collectors.toSet()));
        CLIENT_SIDE.addAll(BlockTags.MOB_INTERACTABLE_DOORS.getStates());

        PANES.addAll(BlockTags.GLASS_PANES.getStates());
        PANES.add(StateTypes.IRON_BARS);
    }

    public static boolean isStairs(PacketStateType type) {
        return BlockTags.STAIRS.contains(type);
    }

    public static boolean isSlab(PacketStateType type) {
        return BlockTags.SLABS.contains(type);
    }

    public static boolean isWall(PacketStateType type) {
        return BlockTags.WALLS.contains(type);
    }

    public static boolean isButton(PacketStateType type) {
        return BlockTags.BUTTONS.contains(type);
    }

    public static boolean isFence(PacketStateType type) {
        return BlockTags.FENCES.contains(type);
    }

    public static boolean isGate(PacketStateType type) {
        return BlockTags.FENCE_GATES.contains(type);
    }

    public static boolean isBed(PacketStateType type) {
        return BlockTags.BEDS.contains(type);
    }

    public static boolean isAir(PacketStateType type) {
        return type.isAir();
    }

    public static boolean isLeaves(PacketStateType type) {
        return BlockTags.LEAVES.contains(type);
    }

    public static boolean isDoor(PacketStateType type) {
        return BlockTags.DOORS.contains(type);
    }

    public static boolean isShulker(PacketStateType type) {
        return BlockTags.SHULKER_BOXES.contains(type);
    }

    public static boolean isGlassBlock(PacketStateType type) {
        return BlockTags.GLASS_BLOCKS.contains(type);
    }

    public static Set<PacketStateType> getPanes() {
        return new HashSet<>(PANES);
    }

    public static boolean isGlassPane(PacketStateType type) {
        return PANES.contains(type);
    }

    public static boolean isCauldron(PacketStateType type) {
        return BlockTags.CAULDRONS.contains(type);
    }

    public static boolean isWaterModern(PacketStateType type) {
        return WATER_LIQUIDS.contains(type);
    }

    public static boolean isWaterLegacy(PacketStateType type) {
        return WATER_LIQUIDS_LEGACY.contains(type);
    }

    public static boolean isShapeExceedsCube(PacketStateType type) {
        return type.exceedsCube();
    }

    public static boolean isUsable(ItemType material) {
        return material != null && (material.hasAttribute(PacketItemAttribute.EDIBLE) || material == PacketItemTypes.POTION || material == PacketItemTypes.MILK_BUCKET
                || material == PacketItemTypes.CROSSBOW || material == PacketItemTypes.BOW || material.toString().endsWith("SWORD")
                || material == PacketItemTypes.TRIDENT || material == PacketItemTypes.SHIELD);
    }

    public static boolean isWater(ClientVersion clientVersion, WrappedBlockState state) {
        boolean modern = clientVersion.isNewerThanOrEquals(ClientVersion.V_1_13);

        if (modern && isWaterModern(state.getType())) {
            return true;
        }

        if (!modern && isWaterLegacy(state.getType())) {
            return true;
        }

        return isWaterlogged(clientVersion, state);
    }

    public static boolean isWaterSource(ClientVersion clientVersion, WrappedBlockState state) {
        if (isWaterlogged(clientVersion, state)) {
            return true;
        }
        if (state.getType() == StateTypes.WATER && state.getLevel() == 0) {
            return true;
        }
        boolean modern = clientVersion.isNewerThanOrEquals(ClientVersion.V_1_13);
        return modern ? WATER_SOURCES.contains(state.getType()) : WATER_SOURCES_LEGACY.contains(state.getType());
    }

    public static boolean isWaterlogged(ClientVersion clientVersion, WrappedBlockState state) {
        if (clientVersion.isOlderThanOrEquals(ClientVersion.V_1_12_2)) return false;
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_13))
            return false;

        PacketStateType type = state.getType();

        // Waterlogged lanterns were added in 1.16.2
        if (clientVersion.isOlderThan(ClientVersion.V_1_16_2) && (type == StateTypes.LANTERN || type == StateTypes.SOUL_LANTERN))
            return false;
        // ViaVersion small dripleaf -> fern (not waterlogged)
        if (clientVersion.isOlderThan(ClientVersion.V_1_17) && type == StateTypes.SMALL_DRIPLEAF)
            return false;
        // Waterlogged rails were added in 1.17
        if (clientVersion.isOlderThan(ClientVersion.V_1_17) && BlockTags.RAILS.contains(type))
            return false;
        // Nice check to see if waterlogged :)
        return (boolean) state.getInternalData().getOrDefault(StateValue.WATERLOGGED, false);
    }

    public static boolean isPlaceableWaterBucket(PacketItemType mat) {
        return mat == PacketItemTypes.AXOLOTL_BUCKET || mat == PacketItemTypes.COD_BUCKET || mat == PacketItemTypes.PUFFERFISH_BUCKET
                || mat == PacketItemTypes.SALMON_BUCKET || mat == PacketItemTypes.TROPICAL_FISH_BUCKET || mat == PacketItemTypes.WATER_BUCKET
                || mat == PacketItemTypes.TADPOLE_BUCKET;
    }

    public static PacketStateType transformBucketMaterial(PacketItemType mat) {
        if (mat == PacketItemTypes.LAVA_BUCKET) return StateTypes.LAVA;
        if (isPlaceableWaterBucket(mat)) return StateTypes.WATER;
        return null;
    }

    // We are taking a shortcut here for the sake of speed and reducing world lookups
    // As we have already assumed that the player does not have water at this block
    // We do not have to track all the version differences in terms of looking for water
    // For 1.7-1.12 clients, it is safe to check SOLID_BLACKLIST directly
    public static boolean isSolidBlockingBlacklist(PacketStateType mat, ClientVersion ver) {
        // Thankfully Mojang has not changed this code much across versions
        // There very likely is a few lurking issues though, I've done my best but can't thoroughly compare 11 versions
        // but from a look, Mojang seems to keep this definition consistent throughout their game (thankfully)
        //
        // What I do is look at 1.8, 1.12, and 1.17 source code, and when I see a difference, I find the version
        // that added it.  I could have missed something if something was added to the blacklist in 1.9 but
        // was removed from it in 1.10 (although this is unlikely as the blacklist rarely changes)
        if (!mat.isBlocking()) return true;

        // 1.13-1.15 had banners on the blacklist - removed in 1.16, not implemented in 1.12 and below
        if (BlockTags.BANNERS.contains(mat))
            return ver.isNewerThanOrEquals(ClientVersion.V_1_13) && ver.isOlderThan(ClientVersion.V_1_16);

        return false;
    }

    public static boolean isAnvil(PacketStateType mat) {
        return BlockTags.ANVIL.contains(mat);
    }

    public static boolean isWoodenChest(PacketStateType mat) {
        return mat == StateTypes.CHEST || mat == StateTypes.TRAPPED_CHEST;
    }

    public static boolean isNoPlaceLiquid(PacketStateType material) {
        return NO_PLACE_LIQUIDS.contains(material);
    }

    public static boolean isWaterIgnoringWaterlogged(ClientVersion clientVersion, WrappedBlockState state) {
        if (clientVersion.isNewerThanOrEquals(ClientVersion.V_1_13))
            return isWaterModern(state.getType());
        return isWaterLegacy(state.getType());
    }

    public static boolean isClientSideInteractable(PacketStateType material) {
        return CLIENT_SIDE.contains(material);
    }

    public static boolean isClientSideOpenableDoor(PacketStateType mat, ClientVersion ver) {
        // Iron doors and all other blocks are not openable
        if (!BlockTags.MOB_INTERACTABLE_DOORS.contains(mat)) {
            return false;
        }

        // Copper doors can only be opened in 1.20.3 and above, in older versions they appear as iron doors
        if (COPPER_DOORS.contains(mat)) {
            return ver.isNewerThanOrEquals(ClientVersion.V_1_20_3);
        }

        // If it's not a copper door players in any version can open it
        return true;
    }

    public static boolean isClientSideOpenableTrapdoor(PacketStateType mat, ClientVersion ver) {
        // Everything except trapdoors
        if (!BlockTags.TRAPDOORS.contains(mat)) {
            return false;
        }

        // In 1.7, only oak trapdoors exist so 1.7 players can open every type of trapdoor
        if (ver.isOlderThan(ClientVersion.V_1_8)) {
            return true;
        }

        // Copper trapdoors can only be opened in 1.20.3 and above, in older versions they appear as iron trapdoors
        if (COPPER_TRAPDOORS.contains(mat)) {
            return ver.isNewerThanOrEquals(ClientVersion.V_1_20_3);
        }

        // If it's not a copper trapdoor players in any version can open it
        return true;
    }

    public static boolean isCompostable(PacketItemType material) {
        // This 3772 character line was auto generated
        return PacketItemTypes.JUNGLE_LEAVES.equals(material) || PacketItemTypes.OAK_LEAVES.equals(material) || PacketItemTypes.SPRUCE_LEAVES.equals(material) || PacketItemTypes.DARK_OAK_LEAVES.equals(material) || PacketItemTypes.ACACIA_LEAVES.equals(material) || PacketItemTypes.BIRCH_LEAVES.equals(material) || PacketItemTypes.AZALEA_LEAVES.equals(material) || PacketItemTypes.OAK_SAPLING.equals(material) || PacketItemTypes.SPRUCE_SAPLING.equals(material) || PacketItemTypes.BIRCH_SAPLING.equals(material) || PacketItemTypes.JUNGLE_SAPLING.equals(material) || PacketItemTypes.ACACIA_SAPLING.equals(material) || PacketItemTypes.DARK_OAK_SAPLING.equals(material) || PacketItemTypes.BEETROOT_SEEDS.equals(material) || PacketItemTypes.DRIED_KELP.equals(material) || PacketItemTypes.SHORT_GRASS.equals(material) || PacketItemTypes.KELP.equals(material) || PacketItemTypes.MELON_SEEDS.equals(material) || PacketItemTypes.PUMPKIN_SEEDS.equals(material) || PacketItemTypes.SEAGRASS.equals(material) || PacketItemTypes.SWEET_BERRIES.equals(material) || PacketItemTypes.GLOW_BERRIES.equals(material) || PacketItemTypes.WHEAT_SEEDS.equals(material) || PacketItemTypes.MOSS_CARPET.equals(material) || PacketItemTypes.SMALL_DRIPLEAF.equals(material) || PacketItemTypes.HANGING_ROOTS.equals(material) || PacketItemTypes.DRIED_KELP_BLOCK.equals(material) || PacketItemTypes.TALL_GRASS.equals(material) || PacketItemTypes.AZALEA.equals(material) || PacketItemTypes.CACTUS.equals(material) || PacketItemTypes.SUGAR_CANE.equals(material) || PacketItemTypes.VINE.equals(material) || PacketItemTypes.NETHER_SPROUTS.equals(material) || PacketItemTypes.WEEPING_VINES.equals(material) || PacketItemTypes.TWISTING_VINES.equals(material) || PacketItemTypes.MELON_SLICE.equals(material) || PacketItemTypes.GLOW_LICHEN.equals(material) || PacketItemTypes.SEA_PICKLE.equals(material) || PacketItemTypes.LILY_PAD.equals(material) || PacketItemTypes.PUMPKIN.equals(material) || PacketItemTypes.CARVED_PUMPKIN.equals(material) || PacketItemTypes.MELON.equals(material) || PacketItemTypes.APPLE.equals(material) || PacketItemTypes.BEETROOT.equals(material) || PacketItemTypes.CARROT.equals(material) || PacketItemTypes.COCOA_BEANS.equals(material) || PacketItemTypes.POTATO.equals(material) || PacketItemTypes.WHEAT.equals(material) || PacketItemTypes.BROWN_MUSHROOM.equals(material) || PacketItemTypes.RED_MUSHROOM.equals(material) || PacketItemTypes.MUSHROOM_STEM.equals(material) || PacketItemTypes.CRIMSON_FUNGUS.equals(material) || PacketItemTypes.WARPED_FUNGUS.equals(material) || PacketItemTypes.NETHER_WART.equals(material) || PacketItemTypes.CRIMSON_ROOTS.equals(material) || PacketItemTypes.WARPED_ROOTS.equals(material) || PacketItemTypes.SHROOMLIGHT.equals(material) || PacketItemTypes.DANDELION.equals(material) || PacketItemTypes.POPPY.equals(material) || PacketItemTypes.BLUE_ORCHID.equals(material) || PacketItemTypes.ALLIUM.equals(material) || PacketItemTypes.AZURE_BLUET.equals(material) || PacketItemTypes.RED_TULIP.equals(material) || PacketItemTypes.ORANGE_TULIP.equals(material) || PacketItemTypes.WHITE_TULIP.equals(material) || PacketItemTypes.PINK_TULIP.equals(material) || PacketItemTypes.OXEYE_DAISY.equals(material) || PacketItemTypes.CORNFLOWER.equals(material) || PacketItemTypes.LILY_OF_THE_VALLEY.equals(material) || PacketItemTypes.WITHER_ROSE.equals(material) || PacketItemTypes.FERN.equals(material) || PacketItemTypes.SUNFLOWER.equals(material) || PacketItemTypes.LILAC.equals(material) || PacketItemTypes.ROSE_BUSH.equals(material) || PacketItemTypes.PEONY.equals(material) || PacketItemTypes.LARGE_FERN.equals(material) || PacketItemTypes.SPORE_BLOSSOM.equals(material) || PacketItemTypes.MOSS_BLOCK.equals(material) || PacketItemTypes.BIG_DRIPLEAF.equals(material) || PacketItemTypes.HAY_BLOCK.equals(material) || PacketItemTypes.BROWN_MUSHROOM_BLOCK.equals(material) || PacketItemTypes.RED_MUSHROOM_BLOCK.equals(material) || PacketItemTypes.NETHER_WART_BLOCK.equals(material) || PacketItemTypes.WARPED_WART_BLOCK.equals(material) || PacketItemTypes.FLOWERING_AZALEA.equals(material) || PacketItemTypes.BREAD.equals(material) || PacketItemTypes.BAKED_POTATO.equals(material) || PacketItemTypes.COOKIE.equals(material) || PacketItemTypes.CAKE.equals(material) || PacketItemTypes.PUMPKIN_PIE.equals(material);
    }
}
