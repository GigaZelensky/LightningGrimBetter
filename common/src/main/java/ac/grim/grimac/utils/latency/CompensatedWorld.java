package ac.grim.grimac.utils.latency;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.data.boxes.BaseSCB;
import ac.grim.grimac.api.data.world.ICompensatedWorld;
import ac.grim.grimac.api.packet.MCPacket;
import ac.grim.grimac.api.packet.block.PacketBlockState;
import ac.grim.grimac.api.packet.item.PacketStateType;
import ac.grim.grimac.api.packet.player.PacketUser;
import ac.grim.grimac.api.packet.player.enums.DiggingAction;
import ac.grim.grimac.api.packet.protocol.PacketClientVersion;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.types.Packet;
import ac.grim.grimac.api.packet.types.client.play.ClientPlayerDiggingPacket;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import ac.grim.grimac.api.packet.world.PacketStateTypes;
import ac.grim.grimac.api.packet.world.chunk.PacketChunk;
import ac.grim.grimac.api.packet.world.enums.North;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.change.BlockModification;
import ac.grim.grimac.utils.chunks.Column;
import ac.grim.grimac.utils.collisions.CollisionData;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.BlockPrediction;
import ac.grim.grimac.utils.data.Pair;
import ac.grim.grimac.api.data.PistonData;
import ac.grim.grimac.utils.data.ShulkerData;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.data.packetentity.PacketEntityShulker;
import ac.grim.grimac.utils.math.GrimMath;
import ac.grim.grimac.api.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.Collisions;
import ac.grim.grimac.utils.nmsutil.GetBoundingBox;
import ac.grim.grimac.utils.nmsutil.Materials;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import ac.grim.grimac.api.packet.entity.PacketEntityTypes;
import ac.grim.grimac.api.packet.world.enums.BlockFace;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v1_16.Chunk_v1_9;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.LegacyFlexibleStorage;
import com.github.retrooper.packetevents.protocol.world.dimension.DimensionType;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import ac.grim.grimac.api.packet.world.enums.East;
import ac.grim.grimac.api.packet.world.enums.Half;
import ac.grim.grimac.api.packet.world.enums.South;
import ac.grim.grimac.api.packet.world.enums.West;
import com.github.retrooper.packetevents.protocol.world.states.type.StateValue;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3i;
import ac.grim.grimac.api.packet.types.client.play.ClientPlayerBlockPlacementPacket;
import ac.grim.grimac.api.packet.types.client.play.ClientPlayerUseItemPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import lombok.Getter;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Inspired by https://github.com/GeyserMC/Geyser/blob/master/connector/src/main/java/org/geysermc/connector/network/session/cache/ChunkCache.java
public class CompensatedWorld implements ICompensatedWorld {
    public static final PacketClientVersion blockVersion = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    private static final PacketBlockState airData = PacketBlockState.getByGlobalId(blockVersion, 0);
    public final GrimPlayer player;
    public final Long2ObjectMap<Column> chunks;
    // Packet locations for blocks
    public Set<PistonData> activePistons = new HashSet<>();
    public Set<ShulkerData> openShulkerBoxes = new HashSet<>();
    // 1.17 with datapacks, and 1.18, have negative world offset values
    @Getter
    private int minHeight = 0;
    @Getter
    private int maxHeight = 256;

    // When the player changes the blocks, they track what the server thinks the blocks are
    //
    // Pair of the block position and the owning list TO the actual block
    // The owning list is so that this info can be removed when the final list is processed
    private final Long2ObjectOpenHashMap<BlockPrediction> originalServerBlocks = new Long2ObjectOpenHashMap<>();
    // Blocks the client changed while placing or breaking blocks
    private List<ImmutableVector3i> currentlyChangedBlocks = new LinkedList<>();
    private final Int2ObjectMap<List<ImmutableVector3i>> serverIsCurrentlyProcessingThesePredictions = new Int2ObjectOpenHashMap<>();
    private final Object2ObjectLinkedOpenHashMap<Pair<ImmutableVector3i, ac.grim.grimac.api.packet.player.enums.DiggingAction>, ImmutableVector3d> unackedActions = new Object2ObjectLinkedOpenHashMap<>();
    private boolean isCurrentlyPredicting = false;
    public boolean isRaining = false;

    private final boolean noNegativeBlocks;

    public CompensatedWorld(GrimPlayer player) {
        this.player = player;
        chunks = new Long2ObjectOpenHashMap<>(81, 0.5f);
        noNegativeBlocks = player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_16_4);
    }

    public void startPredicting() {
        if (player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_18_2)) return; // No predictions
        this.isCurrentlyPredicting = true;
    }

    public void handlePredictionConfirmation(int prediction) {
        for (Iterator<Int2ObjectMap.Entry<List<ImmutableVector3i>>> it = serverIsCurrentlyProcessingThesePredictions.int2ObjectEntrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, List<ImmutableVector3i>> iter = it.next();
            if (iter.getKey() <= prediction) {
                applyBlockChanges(iter.getValue());
                it.remove();
            } else {
                break;
            }
        }
    }

    public void handleBlockBreakAck(ImmutableVector3i blockPos, int blockState, DiggingAction action, boolean accepted) {
        if (!accepted || action != DiggingAction.START_DIGGING || !unackedActions.containsKey(new Pair<>(blockPos, action))) {
            player.sendTransaction(); // This packet actually matters
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                ImmutableVector3d playerPos = unackedActions.remove(new Pair<>(blockPos, action));
                handleAck(blockPos, blockState, playerPos);
            });
        } else {
            unackedActions.remove(new Pair<>(blockPos, action));
        }

        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
            while (unackedActions.size() >= 50) {
                this.unackedActions.removeFirst();
            }
        });
    }

    private void applyBlockChanges(List<ImmutableVector3i> toApplyBlocks) {
        player.sendTransaction();
        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> toApplyBlocks.forEach(vector3i -> {
            BlockPrediction predictionData = originalServerBlocks.get(vector3i.getSerializedPosition());

            // We are the last to care about this prediction, remove it to stop memory leak
            // Block changes are allowed to execute out of order, because it actually doesn't matter
            if (predictionData != null && predictionData.getForBlockUpdate() == toApplyBlocks) {
                originalServerBlocks.remove(vector3i.getSerializedPosition());
                handleAck(vector3i, predictionData.getOriginalBlockId(), predictionData.getPlayerPosition());
            }
        }));
    }

    private void handleAck(ImmutableVector3i vector3i, int originalBlockId, ImmutableVector3d playerPosition) {
        // If we need to change the world block state
        if (getBlock(vector3i).getGlobalId() != originalBlockId) {
            player.blockHistory.add(
                    new BlockModification(
                            getBlock(vector3i),
                            PacketBlockState.getByGlobalId(originalBlockId),
                            vector3i,
                            GrimAPI.INSTANCE.getTickManager().currentTick,
                            BlockModification.Cause.HANDLE_NETTY_SYNC_TRANSACTION
                    )
            );
            updateBlock(vector3i.getX(), vector3i.getY(), vector3i.getZ(), originalBlockId);
            PacketBlockState state = PacketBlockState.getByGlobalId(blockVersion, originalBlockId);

            // The player will teleport themselves if they get stuck in the reverted block
            if (playerPosition != null && CollisionData.getData(state.getType()).getMovementCollisionBox(player, player.getClientVersion(), state, vector3i.getX(), vector3i.getY(), vector3i.getZ()).isIntersected(player.boundingBox)) {
                player.lastX = player.x;
                player.lastY = player.y;
                player.lastZ = player.z;
                player.x = playerPosition.getX();
                player.y = playerPosition.getY();
                player.z = playerPosition.getZ();
                player.boundingBox = GetBoundingBox.getCollisionBoxForPlayer(player, player.x, player.y, player.z);
            }
        }
    }

    public void handleBlockBreakPrediction(ClientPlayerDiggingPacket digging) {
        // 1.14.4 intentional and correct, do not change it to 1.14
        if (player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_14_4) && player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_18_2)) {
            unackedActions.put(new Pair<>(digging.getBlockPosition(), digging.getDiggingAction()), MCPacket.getAPI().getVectorFactory().getImmutableVec3d(player.x, player.y, player.z));
        }
    }

    public void stopPredicting(Packet wrapper) {
        if (player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_18_2)) return; // No predictions
        this.isCurrentlyPredicting = false; // We aren't in a block place or use item

        if (this.currentlyChangedBlocks.isEmpty()) return; // Nothing to change

        List<ImmutableVector3i> toApplyBlocks = this.currentlyChangedBlocks; // We must now track the client applying the server predicted blocks
        this.currentlyChangedBlocks = new LinkedList<>(); // Reset variable without changing original

        // We don't need to simulate any packets, it is native to the version we are on
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19)) {
            // Pull the confirmation ID out of the packet
            int confirmationId = 0;
            if (wrapper instanceof ClientPlayerBlockPlacementPacket) {
                confirmationId = ((ClientPlayerBlockPlacementPacket) wrapper).getSequence();
            } else if (wrapper instanceof ClientPlayerUseItemPacket) {
                confirmationId = ((ClientPlayerUseItemPacket) wrapper).getSequence();
            } else if (wrapper instanceof ClientPlayerDiggingPacket) {
                confirmationId = ((ClientPlayerDiggingPacket) wrapper).getSequence();
            }

            serverIsCurrentlyProcessingThesePredictions.put(confirmationId, toApplyBlocks);
        } else {
            // ViaVersion is updated and runs tasks with bukkit which is correct
            // So we must wait for the bukkit thread to start ticking so via can "confirm" it
            //
            // no need to support Folia on this one because Folia is 1.19+ only
            GrimAPI.INSTANCE.getScheduler().getGlobalRegionScheduler().run(GrimAPI.INSTANCE.getGrimPlugin(), () -> {
                // And then we jump back to the netty thread to simulate that Via sent the confirmation
                ChannelHelper.runInEventLoop(player.user.getChannel(), () -> applyBlockChanges(toApplyBlocks));
            });
        }
    }

    public static long chunkPositionToLong(int x, int z) {
        return ((x & 0xFFFFFFFFL) << 32L) | (z & 0xFFFFFFFFL);
    }

    public boolean isNearHardEntity(SimpleCollisionBox playerBox) {
        for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
            if ((entity.isBoat() || entity.getType() == PacketEntityTypes.SHULKER) && player.compensatedEntities.self.getRiding() != entity) {
                SimpleCollisionBox box = entity.getPossibleCollisionBoxes();
                if (box.isIntersected(playerBox)) {
                    return true;
                }
            }
        }

        // Also block entities
        for (ShulkerData data : openShulkerBoxes) {
            SimpleCollisionBox shulkerCollision = data.getCollision();
            if (playerBox.isCollided(shulkerCollision)) {
                return true;
            }
        }

        // Pistons are a block entity.
        for (PistonData data : activePistons) {
            for (BaseSCB box : data.boxes) {
                if (playerBox.isCollided(box)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static PacketChunk create() {
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_18)) {
            return new Chunk_v1_18();
        } else if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_16)) {
            return new Chunk_v1_9(0, DataPalette.createForChunk());
        }
        return new Chunk_v1_9(0, new DataPalette(new ListPalette(4), new LegacyFlexibleStorage(4, 4096), PaletteType.CHUNK));
    }

    public void updateBlock(ImmutableVector3i pos, PacketBlockState state) {
        updateBlock(pos.getX(), pos.getY(), pos.getZ(), state.getGlobalId());
    }

    public void updateBlock(int x, int y, int z, int combinedID) {
        ImmutableVector3i asVector = MCPacket.getAPI().getVectorFactory().getImmutableVec3i(x, y, z);
        BlockPrediction prediction = originalServerBlocks.get(asVector.getSerializedPosition());

        if (isCurrentlyPredicting) {
            if (prediction == null) {
                originalServerBlocks.put(asVector.getSerializedPosition(), new BlockPrediction(currentlyChangedBlocks, asVector, getBlock(asVector).getGlobalId(), MCPacket.getAPI().getVectorFactory().getImmutableVec3d(player.x, player.y, player.z))); // Remember server controlled block type
            } else {
                prediction.setForBlockUpdate(currentlyChangedBlocks); // Block existing there was placed by client, mark block to have a new prediction
            }
            currentlyChangedBlocks.add(asVector);
        }

        if (!isCurrentlyPredicting && prediction != null) {
            // Server has a more up-to-date block, although client is more recent, replace the original serialized position
            prediction.setOriginalBlockId(combinedID);
            return;
        }

        Column column = getChunk(x >> 4, z >> 4);

        // Apply 1.17 expanded world offset
        int offsetY = y - minHeight;

        if (column != null) {
            if (column.chunks().length <= (offsetY >> 4) || (offsetY >> 4) < 0) return;

            PacketChunk chunk = column.chunks()[offsetY >> 4];

            if (chunk == null) {
                chunk = create();
                column.chunks()[offsetY >> 4] = chunk;

                // Sets entire chunk to air
                // This glitch/feature occurs due to the palette size being 0 when we first create a chunk section
                // Meaning that all blocks in the chunk will refer to palette #0, which we are setting to air
                chunk.set(0, 0, 0, 0);
            }

            // The method also gets called for the previous state before replacement
            player.pointThreeEstimator.handleChangeBlock(x, y, z, chunk.get(blockVersion, x & 0xF, offsetY & 0xF, z & 0xF));

            chunk.set(x & 0xF, offsetY & 0xF, z & 0xF, combinedID);

            // Handle stupidity such as fluids changing in idle ticks.
            player.pointThreeEstimator.handleChangeBlock(x, y, z, PacketBlockState.getByGlobalId(blockVersion, combinedID));
        }
    }

    public void tickOpenable(int blockX, int blockY, int blockZ) {
        final PacketBlockState data = getBlock(blockX, blockY, blockZ);
        final PacketStateType type = data.getType();
        if (Materials.isClientSideOpenableDoor(type, player.getClientVersion())) {
            PacketBlockState otherDoor = getBlock(blockX,
                    blockY + (data.half() == Half.LOWER ? 1 : -1), blockZ);

            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
                if (BlockTags.DOORS.contains(otherDoor.getType())) {
                    otherDoor.setOpen(!otherDoor.isOpen());
                    updateBlock(blockX, blockY + (data.half() == Half.LOWER ? 1 : -1), blockZ, otherDoor.getGlobalId());
                }
                data.setOpen(!data.isOpen());
                updateBlock(blockX, blockY, blockZ, data.getGlobalId());
            } else {
                // 1.12 attempts to change the bottom half of the door first
                if (data.half() == Half.LOWER) {
                    data.setOpen(!data.isOpen());
                    updateBlock(blockX, blockY, blockZ, data.getGlobalId());
                } else if (BlockTags.DOORS.contains(otherDoor.getType()) && otherDoor.half() == Half.LOWER) {
                    // Then tries setting the first bit of whatever is below it, disregarding it's type
                    otherDoor.setOpen(!otherDoor.isOpen());
                    updateBlock(blockX, blockY - 1, blockZ, otherDoor.getGlobalId());
                }
            }
        } else if (Materials.isClientSideOpenableTrapdoor(type, player.getClientVersion()) || BlockTags.FENCE_GATES.contains(type)) {
            // Take 12 most significant bytes -> the material ID.  Combine them with the new block magic data.
            data.setOpen(!data.isOpen());
            updateBlock(blockX, blockY, blockZ, data.getGlobalId());
        } else if (BlockTags.BUTTONS.contains(type)) {
            data.setPowered(true);
        }
    }

    public void tickPlayerInPistonPushingArea() {
        player.uncertaintyHandler.tick();
        // Occurs on player login
        if (player.boundingBox == null) return;

        SimpleCollisionBox expandedBB = GetBoundingBox.getBoundingBoxFromPosAndSize(player, player.lastX, player.lastY, player.lastZ, 0.001f, 0.001f);
        expandedBB.expandToAbsoluteCoordinates(player.x, player.y, player.z);
        SimpleCollisionBox playerBox = expandedBB.copy().expand(1);

        double modX = 0;
        double modY = 0;
        double modZ = 0;

        for (PistonData data : activePistons) {
            for (BaseSCB box : data.boxes) {
                if (playerBox.isCollided(box)) {
                    modX = Math.max(modX, Math.abs(data.direction.getModX() * 0.51D));
                    modY = Math.max(modY, Math.abs(data.direction.getModY() * 0.51D));
                    modZ = Math.max(modZ, Math.abs(data.direction.getModZ() * 0.51D));

                    playerBox.expandMax(modX, modY, modZ);
                    playerBox.expandMin(modX * -1, modY * -1, modZ * -1);

                    if (data.hasSlimeBlock || (data.hasHoneyBlock && player.getClientVersion().isOlderThan(PacketClientVersions.V_1_15_2))) {
                        player.uncertaintyHandler.slimePistonBounces.add(data.direction);
                    }

                    break;
                }
            }
        }

        for (ShulkerData data : openShulkerBoxes) {
            SimpleCollisionBox shulkerCollision = data.getCollision();

            BlockFace direction;
            if (data.entity == null) {
                PacketBlockState state = getBlock(data.blockPos.getX(), data.blockPos.getY(), data.blockPos.getZ());
                direction = state.facing();
            } else {
                direction = ((PacketEntityShulker) data.entity).facing.getOppositeFace();
            }

            if (direction == null) direction = BlockFace.UP; // default state

            // Change negative corner in expansion as the direction is negative
            // We don't bother differentiating shulker entities and shulker boxes
            // I guess players can cheat to get an extra 0.49 of Y height on shulker boxes, I don't care.
            if (direction.getModX() == -1 || direction.getModY() == -1 || direction.getModZ() == -1) {
                shulkerCollision.expandMin(direction.getModX(), direction.getModY(), direction.getModZ());
            } else {
                shulkerCollision.expandMax(direction.getModZ(), direction.getModY(), direction.getModZ());
            }

            if (playerBox.isCollided(shulkerCollision)) {
                modX = Math.max(modX, Math.abs(direction.getModX() * 0.51D));
                modY = Math.max(modY, Math.abs(direction.getModY() * 0.51D));
                modZ = Math.max(modZ, Math.abs(direction.getModZ() * 0.51D));

                playerBox.expandMax(modX, modY, modZ);
                playerBox.expandMin(modX, modY, modZ);

                player.uncertaintyHandler.isSteppingNearShulker = true;
            }
        }

        player.uncertaintyHandler.pistonX.add(modX);
        player.uncertaintyHandler.pistonY.add(modY);
        player.uncertaintyHandler.pistonZ.add(modZ);

        removeInvalidPistonLikeStuff(0);
    }

    public void removeInvalidPistonLikeStuff(int transactionId) {
        // Tick the pistons and remove them if they can no longer exist
        if (transactionId != 0) {
            activePistons.removeIf(data -> data.lastTransactionSent < transactionId);
            openShulkerBoxes.removeIf(data -> data.isClosing && data.lastTransactionSent < transactionId);
        } else {
            activePistons.removeIf(PistonData::tickIfGuaranteedFinished);
            openShulkerBoxes.removeIf(ShulkerData::tickIfGuaranteedFinished);
        }
        // Remove if a shulker is not in this block position anymore
        openShulkerBoxes.removeIf(box -> {
            if (box.blockPos != null) { // Block is no longer valid
                return !Materials.isShulker(getBlock(box.blockPos).getType());
            } else { // Entity is no longer valid
                return !player.compensatedEntities.entityMap.containsValue(box.entity);
            }
        });
    }

    public PacketBlockState getBlock(ImmutableVector3i position) {
        return getBlock(position.getX(), position.getY(), position.getZ());
    }

    public PacketBlockState getBlock(int x, int y, int z) {
        if (noNegativeBlocks && y < 0) return airData;

        try {
            Column column = getChunk(x >> 4, z >> 4);

            y -= minHeight;
            if (column == null || y < 0 || (y >> 4) >= column.chunks().length) return airData;

            PacketChunk chunk = column.chunks()[y >> 4];
            if (chunk != null) {
                return chunk.get(blockVersion, x & 0xF, y & 0xF, z & 0xF);
            }
        } catch (Exception ignored) {
        }

        return airData;
    }

    // Not direct power into a block
    // Trapped chests give power but there's no packet to the client to actually apply this... ignore trapped chests
    // just like mojang did!
    public int getRawPowerAtState(BlockFace face, int x, int y, int z) {
        PacketBlockState block = getBlock(x, y, z);

        if (block.getType() == PacketStateTypes.REDSTONE_BLOCK) {
            return 15;
        } else if (block.getType() == PacketStateTypes.DETECTOR_RAIL) { // Rails have directional requirement
            return block.isPowered() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.REDSTONE_TORCH) {
            return face != BlockFace.UP && block.isLit() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.REDSTONE_WIRE) {
            BlockFace needed = face.getOppositeFace();

            BlockFace badOne = needed.getCW();
            BlockFace badTwo = needed.getCCW();

            boolean isPowered = false;
            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
                switch (needed) {
                    case DOWN:
                        isPowered = true;
                        break;
                    case NORTH:
                        isPowered = block.north() == North.TRUE;
                        if (isPowered && (badOne == BlockFace.NORTH || badTwo == BlockFace.NORTH)) {
                            return 0;
                        }
                        break;
                    case SOUTH:
                        isPowered = block.south() == South.TRUE;
                        if (isPowered && (badOne == BlockFace.SOUTH || badTwo == BlockFace.SOUTH)) {
                            return 0;
                        }
                        break;
                    case WEST:
                        isPowered = block.west() == West.TRUE;
                        if (isPowered && (badOne == BlockFace.WEST || badTwo == BlockFace.WEST)) {
                            return 0;
                        }
                        break;
                    case EAST:
                        isPowered = block.east() == East.TRUE;
                        if (isPowered && (badOne == BlockFace.EAST || badTwo == BlockFace.EAST)) {
                            return 0;
                        }
                        break;
                }
            } else {
                isPowered = true; // whatever, just go off the block's power to see if it connects
            }

            return isPowered ? block.getPower() : 0;
        } else if (block.getType() == PacketStateTypes.REDSTONE_WALL_TORCH) {
            return block.facing() != face && block.isLit() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.DAYLIGHT_DETECTOR) {
            return block.getPower();
        } else if (block.getType() == PacketStateTypes.OBSERVER) {
            return block.facing() == face && block.isPowered() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.REPEATER) {
            return block.facing() == face && block.isPowered() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.LECTERN) {
            return block.isPowered() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.TARGET) {
            return block.getPower();
        }

        return 0;
    }

    // Redstone can power blocks indirectly by directly powering a block next to the block to power
    public int getDirectSignalAtState(BlockFace face, int x, int y, int z) {
        PacketBlockState block = getBlock(x, y, z);

        if (block.getType() == PacketStateTypes.DETECTOR_RAIL) { // Rails hard power block below itself
            // TODO (Packet Rewrite) confirm replacement is correct
            boolean isPowered = (boolean) block.getInternalData().getOrDefault(StateValue.POWERED, false);
            return face == BlockFace.UP && isPowered ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.REDSTONE_TORCH) {
            return face != BlockFace.UP && block.isLit() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.LEVER || BlockTags.BUTTONS.contains(block.getType())) {
            return block.facing().getOppositeFace() == face && block.isPowered() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.REDSTONE_WALL_TORCH) {
            return face == BlockFace.DOWN && block.isPowered() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.LECTERN) {
            return face == BlockFace.UP && block.isPowered() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.OBSERVER) {
            return block.facing() == face && block.isPowered() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.REPEATER) {
            return block.facing() == face && block.isPowered() ? 15 : 0;
        } else if (block.getType() == PacketStateTypes.REDSTONE_WIRE) {
            BlockFace needed = face.getOppositeFace();

            BlockFace badOne = needed.getCW();
            BlockFace badTwo = needed.getCCW();

            boolean isPowered = false;
            switch (needed) {
                case DOWN:
                case UP:
                    break;
                case NORTH:
                    isPowered = block.north() == North.TRUE;
                    if (isPowered && (badOne == BlockFace.NORTH || badTwo == BlockFace.NORTH)) {
                        return 0;
                    }
                    break;
                case SOUTH:
                    isPowered = block.south() == South.TRUE;
                    if (isPowered && (badOne == BlockFace.SOUTH || badTwo == BlockFace.SOUTH)) {
                        return 0;
                    }
                    break;
                case WEST:
                    isPowered = block.west() == West.TRUE;
                    if (isPowered && (badOne == BlockFace.WEST || badTwo == BlockFace.WEST)) {
                        return 0;
                    }
                    break;
                case EAST:
                    isPowered = block.east() == East.TRUE;
                    if (isPowered && (badOne == BlockFace.EAST || badTwo == BlockFace.EAST)) {
                        return 0;
                    }
                    break;
            }

            return isPowered ? block.getPower() : 0;
        }

        return 0;
    }

    public Column getChunk(int chunkX, int chunkZ) {
        long chunkPosition = chunkPositionToLong(chunkX, chunkZ);
        return chunks.get(chunkPosition);
    }

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        long chunkPosition = chunkPositionToLong(chunkX, chunkZ);
        return chunks.containsKey(chunkPosition);
    }

    @Override
    public void addActivePistons(PistonData data) {
        activePistons.add(data);
    }

    public void addToCache(Column chunk, int chunkX, int chunkZ) {
        long chunkPosition = chunkPositionToLong(chunkX, chunkZ);
        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> chunks.put(chunkPosition, chunk));
    }

    public PacketStateType getBlockType(double x, double y, double z) {
        return getBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).getType();
    }

    public PacketBlockState getBlock(double x, double y, double z) {
        return getBlock((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public double getFluidLevelAt(int x, int y, int z) {
        return Math.max(getWaterFluidLevelAt(x, y, z), getLavaFluidLevelAt(x, y, z));
    }

    public boolean isWaterSourceBlock(int x, int y, int z) {
        PacketBlockState bukkitBlock = getBlock(x, y, z);
        return Materials.isWaterSource(player.getClientVersion(), bukkitBlock);
    }

    public boolean containsLiquid(SimpleCollisionBox var0) {
        return Collisions.hasMaterial(player, var0, data -> Materials.isWater(player.getClientVersion(), data.first()) || data.first().getType() == PacketStateTypes.LAVA);
    }

    public double getLavaFluidLevelAt(int x, int y, int z) {
        PacketBlockState magicBlockState = getBlock(x, y, z);
        PacketBlockState magicBlockStateAbove = getBlock(x, y + 1, z);

        if (magicBlockState.getType() != PacketStateTypes.LAVA) return 0;
        if (magicBlockStateAbove.getType() == PacketStateTypes.LAVA) return 1;

        int level = magicBlockState.getLevel();

        // If it is lava or flowing lava
        if (level >= 8) {
            // Falling lava has a level of 8
            return 8 / 9f;
        }

        return (8 - level) / 9f;
    }

    public boolean containsLava(SimpleCollisionBox var0) {
        return Collisions.hasMaterial(player, var0, data -> data.first().getType() == PacketStateTypes.LAVA);
    }

    public double getWaterFluidLevelAt(double x, double y, double z) {
        return getWaterFluidLevelAt(GrimMath.floor(x), GrimMath.floor(y), GrimMath.floor(z));
    }

    public double getWaterFluidLevelAt(int x, int y, int z) {
        PacketBlockState wrappedBlock = getBlock(x, y, z);
        boolean isWater = Materials.isWater(player.getClientVersion(), wrappedBlock);

        if (!isWater) return 0;

        // If water has water above it, it's block height is 1, even if it's waterlogged
        if (Materials.isWater(player.getClientVersion(), getBlock(x, y + 1, z))) {
            return 1;
        }

        // If it is water or flowing water
        if (wrappedBlock.getType() == PacketStateTypes.WATER) {
            int level = wrappedBlock.getLevel();

            // Falling water has a level of 8
            if ((level & 0x8) == 8) return 8 / 9f;

            return (8 - level) / 9f;
        }

        // The block is water, isn't water material directly, and doesn't have block above, so it is waterlogged
        // or another source-like block such as kelp.
        return 8 / 9F;
    }

    public void removeChunkLater(int chunkX, int chunkZ) {
        long chunkPosition = chunkPositionToLong(chunkX, chunkZ);
        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> chunks.remove(chunkPosition));
    }

    public void setDimension(DimensionType dimension, PacketUser user) {
        // No world height NBT
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_17)) return;

        minHeight = dimension.getMinY();
        maxHeight = minHeight + dimension.getHeight();
    }

    public PacketBlockState getBlock(Vector3dm aboveCCWPos) {
        return getBlock(aboveCCWPos.getX(), aboveCCWPos.getY(), aboveCCWPos.getZ());
    }
}
