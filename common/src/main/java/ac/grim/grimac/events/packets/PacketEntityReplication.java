package ac.grim.grimac.events.packets;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.packet.MCPacket;
import ac.grim.grimac.api.packet.entity.EntityData;
import ac.grim.grimac.api.packet.entity.PacketEntityType;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.types.PacketTypes;
import ac.grim.grimac.api.packet.types.event.PacketReceiveEvent;
import ac.grim.grimac.api.packet.types.server.play.*;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.util.LogUtil;
import ac.grim.grimac.utils.data.TrackerData;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.data.packetentity.PacketEntityHook;
import ac.grim.grimac.utils.data.packetentity.PacketEntityTrackXRot;
import ac.grim.grimac.utils.reflection.ViaVersionUtil;
import com.github.retrooper.packetevents.PacketEvents;
import ac.grim.grimac.api.packet.types.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import ac.grim.grimac.api.packet.entity.PacketEntityTypes;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import ac.grim.grimac.api.packet.types.server.play.ServerAttachEntityPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerDestroyEntitiesPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerEntityEffectPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerEntityMetadataPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerEntityPositionSyncPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerEntityRelativeMovePacket;
import ac.grim.grimac.api.packet.types.server.play.ServerEntityRelativeMoveAndRotationPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerEntityRotationPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerEntityStatusPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerPlayerInfoPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerRemoveEntityEffect;
import ac.grim.grimac.api.packet.types.server.play.ServerSetPassengersPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerSpawnEntityPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerSpawnLivingEntityPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerSpawnPaintingPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerSpawnPlayerPacket;
import ac.grim.grimac.api.packet.types.server.play.ServerUpdateAttributesPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class PacketEntityReplication extends Check implements PacketCheck {

    private final AtomicBoolean hasSentPreWavePacket = new AtomicBoolean(true);

    // Let's imagine the player is on a boat.
    // The player breaks this boat
    // If we were to despawn the boat without an extra transaction, then the boat would disappear before
    // it disappeared on the client side, creating a ghost boat to flag checks with
    //
    // If we were to despawn the tick after, spawning must occur the transaction before to stop the same exact
    // problem with ghost boats in reverse.
    //
    // Therefore, we despawn the transaction after, and spawn the tick before.
    //
    // If we despawn then spawn an entity in the same transaction, then this solution would despawn the new entity
    // instead of the old entity, so we wouldn't see the boat at all
    //
    // Therefore, if the server sends a despawn and then a spawn in the same transaction for the same entity,
    // We should simply add a transaction (which will clear this list!)
    //
    // Another valid solution is to simply spam more transactions, but let's not waste bandwidth.
    private final List<Integer> despawnedEntitiesThisTransaction = new ArrayList<>();

    // Maximum ping when a firework boost is removed from the player.
    private int maxFireworkBoostPing = 1000;

    public PacketEntityReplication(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // Teleports don't interpolate, duplicate 1.17 packets don't interpolate
        if (!isTickPacket(event.getPacketType())) return;

        boolean isTickingReliably = player.isTickingReliablyFor(3);

        PacketEntity playerVehicle = player.compensatedEntities.self.getRiding();
        for (PacketEntity entity : player.compensatedEntities.entityMap.values()) {
            if (entity == playerVehicle && !player.vehicleData.lastDummy) {
                // The player has this as their vehicle, so they aren't interpolating it.
                // And it isn't a dummy position
                entity.setPositionRaw(player, entity.getPossibleLocationBoxes());
            } else {
                entity.onMovement(isTickingReliably);
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        // ensure grim is the one that sent the transaction
        if ((event.getPacketType() == PacketTypes.Play.Server.PING || event.getPacketType() == PacketTypes.Play.Server.WINDOW_CONFIRMATION) && player.packetStateData.lastServerTransWasValid) {
            despawnedEntitiesThisTransaction.clear();
        } else if (event.getPacketType() == PacketTypes.Play.Server.SPAWN_LIVING_ENTITY) {
            ServerSpawnLivingEntityPacket packetOutEntity = ServerSpawnLivingEntityPacket.from(event);
            addEntity(packetOutEntity.getEntityId(), packetOutEntity.getEntityUUID(), packetOutEntity.getEntityType(), packetOutEntity.getPosition(), packetOutEntity.getYaw(), packetOutEntity.getPitch(), packetOutEntity.getEntityMetadata(), 0);
        } else if (event.getPacketType() == PacketTypes.Play.Server.SPAWN_ENTITY) {
            ServerSpawnEntityPacket packetOutEntity = ServerSpawnEntityPacket.from(event);
            addEntity(packetOutEntity.getEntityId(), packetOutEntity.getUUID().orElse(null), packetOutEntity.getEntityType(), packetOutEntity.getPosition(), packetOutEntity.getYaw(), packetOutEntity.getPitch(), null, packetOutEntity.getData());
        } else if (event.getPacketType() == PacketTypes.Play.Server.SPAWN_PLAYER) {
            ServerSpawnPlayerPacket packetOutEntity = ServerSpawnPlayerPacket.from(event);
            addEntity(packetOutEntity.getEntityId(), packetOutEntity.getUUID(), PacketEntityTypes.PLAYER, packetOutEntity.getPosition(), packetOutEntity.getYaw(), packetOutEntity.getPitch(), packetOutEntity.getEntityMetadata(), 0);
        } else if (event.getPacketType() == PacketTypes.Play.Server.SPAWN_PAINTING) {
            ServerSpawnPaintingPacket packetOutEntity = ServerSpawnPaintingPacket.from(event);
            addEntity(packetOutEntity.getEntityId(), packetOutEntity.getUUID(), PacketEntityTypes.PAINTING, packetOutEntity.getPosition().toVector3d(), 0, 0f, null, packetOutEntity.getDirection().getHorizontalIndex());
        } else if (event.getPacketType() == PacketTypes.Play.Server.ENTITY_RELATIVE_MOVE) {
            ServerEntityRelativeMovePacket move = ServerEntityRelativeMovePacket.from(event);
            handleMoveEntity(event, move.getEntityId(), move.getDeltaX(), move.getDeltaY(), move.getDeltaZ(), null, null, true, true);
        } else if (event.getPacketType() == PacketTypes.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            ServerEntityRelativeMoveAndRotationPacket move = ServerEntityRelativeMoveAndRotationPacket.from(event);
            handleMoveEntity(event, move.getEntityId(), move.getDeltaX(), move.getDeltaY(), move.getDeltaZ(), move.getYaw() * 0.7111111F, move.getPitch() * 0.7111111F, true, true);
        } else if (event.getPacketType() == PacketTypes.Play.Server.ENTITY_TELEPORT) {
            ServerEntityTeleportPacket move = ServerEntityTeleportPacket.from(event);
            ImmutableVector3d pos = move.getPosition();
            handleMoveEntity(event, move.getEntityId(), pos.getX(), pos.getY(), pos.getZ(), move.getYaw(), move.getPitch(), false, true);
        } else if (event.getPacketType() == PacketTypes.Play.Server.ENTITY_POSITION_SYNC) {
            // ENTITY_TELEPORT but without relative flags
            ServerEntityPositionSyncPacket move = ServerEntityPositionSyncPacket.from(event);
            final ImmutableVector3d pos = move.getPosition();
            // TODO this isn't technically correct
            // If the position sync is to a pos > 4096 from the entity pos, client does some special stuff without interpolation
            handleMoveEntity(event, move.getId(), pos.getX(), pos.getY(), pos.getZ(), move.getYaw(), move.getPitch(), false, true);
        } else if (event.getPacketType() == PacketTypes.Play.Server.ENTITY_ROTATION) { // Affects interpolation
            ServerEntityRotationPacket move = ServerEntityRotationPacket.from(event);
            handleMoveEntity(event, move.getEntityId(), 0, 0, 0, move.getYaw() * 0.7111111F, move.getPitch() * 0.7111111F, true, false);
        } else if (event.getPacketType() == PacketTypes.Play.Server.ENTITY_METADATA) {
            ServerEntityMetadataPacket entityMetadata = ServerEntityMetadataPacket.from(event);
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.compensatedEntities.updateEntityMetadata(entityMetadata.getEntityId(), entityMetadata.getEntityMetadata()));
        }

        // 1.19.3+
        else if (event.getPacketType() == PacketTypes.Play.Server.PLAYER_INFO_UPDATE) {
            ServerPlayerInfoUpdate info = new ServerPlayerInfoUpdate(event);
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                for (ServerPlayerInfoUpdate.PlayerInfo entry : info.getEntries()) {
                    final UserProfile gameProfile = entry.getGameProfile();
                    final UUID uuid = gameProfile.getUUID();
                    player.compensatedEntities.profiles.put(uuid, gameProfile);
                }
            });
        } else if (event.getPacketType() == PacketTypes.Play.Server.PLAYER_INFO_REMOVE) {
            ServerPlayerInfoRemovePacket remove = ServerPlayerInfoRemovePacket.from(event);
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> remove.getProfileIds().forEach(player.compensatedEntities.profiles::remove));
        } else if (event.getPacketType() == PacketTypes.Play.Server.PLAYER_INFO) {
            ServerPlayerInfoPacket info = new ServerPlayerInfoPacket(event);
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                if (info.getAction() == ServerPlayerInfoPacket.Action.ADD_PLAYER) {
                    for (ServerPlayerInfoPacket.PlayerData entry : info.getPlayerDataList()) {
                        final UserProfile gameProfile = entry.getUserProfile();
                        final UUID uuid = gameProfile.getUUID();
                        player.compensatedEntities.profiles.put(uuid, gameProfile);
                    }
                } else if (info.getAction() == ServerPlayerInfoPacket.Action.REMOVE_PLAYER) {
                    info.getPlayerDataList().forEach(profile -> player.compensatedEntities.profiles.remove(profile.getUserProfile().getUUID()));
                }
            });
        } else if (event.getPacketType() == PacketTypes.Play.Server.ENTITY_EFFECT) {
            ServerEntityEffectPacket effect = new ServerEntityEffectPacket(event);

            PotionType type = effect.getPotionType();

            // ViaVersion tries faking levitation effects and fails badly lol, flagging the anticheat
            // Block other effects just in case ViaVersion gets any ideas
            //
            // Set to 24 so ViaVersion blocks it
            // 24 is the levitation effect
            if (player.getClientVersion().isOlderThan(PacketClientVersions.V_1_9) && ViaVersionUtil.isAvailable() && type.getId(player.getClientVersion()) > 23) {
                event.setCancelled(true);
                return;
            }

            // ViaVersion dolphin's grace also messes us up, set it to a potion effect that doesn't exist on 1.12
            // Effect 31 is bad omen
            if (player.getClientVersion().isOlderThan(PacketClientVersions.V_1_13) && ViaVersionUtil.isAvailable() && type.getId(player.getClientVersion()) == 30) {
                event.setCancelled(true);
                return;
            }

            if (isDirectlyAffectingPlayer(player, effect.getEntityId())) player.sendTransaction();

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                PacketEntity entity = player.compensatedEntities.getEntity(effect.getEntityId());
                if (entity == null) return;

                entity.addPotionEffect(type, effect.getEffectAmplifier());
            });
        } else if (event.getPacketType() == PacketTypes.Play.Server.REMOVE_ENTITY_EFFECT) {
            ServerRemoveEntityEffect effect = new ServerRemoveEntityEffect(event);

            if (isDirectlyAffectingPlayer(player, effect.getEntityId())) player.sendTransaction();

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                PacketEntity entity = player.compensatedEntities.getEntity(effect.getEntityId());
                if (entity == null) return;

                entity.removePotionEffect(effect.getPotionType());
            });
        } else if (event.getPacketType() == PacketTypes.Play.Server.UPDATE_ATTRIBUTES) {
            ServerUpdateAttributesPacket attributes = new ServerUpdateAttributesPacket(event);

            int entityID = attributes.getEntityId();

            // The attributes for this entity is active, currently
            if (isDirectlyAffectingPlayer(player, entityID)) player.sendTransaction();

            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(),
                    () -> player.compensatedEntities.updateAttributes(entityID, attributes.getProperties()));
        } else if (event.getPacketType() == PacketTypes.Play.Server.ENTITY_STATUS) {
            ServerEntityStatusPacket status = new ServerEntityStatusPacket(event);
            // This hasn't changed from 1.7.2 to 1.17
            // Needed to exempt players on dead vehicles, as dead entities have strange physics.
            if (status.getStatus() == 3) {
                PacketEntity entity = player.compensatedEntities.getEntity(status.getEntityId());

                if (entity == null) return;
                entity.isDead = true;
            }

            if (status.getStatus() == 9) {
                if (status.getEntityId() != player.entityID) return;

                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.packetStateData.setSlowedByUsingItem(false));
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> player.packetStateData.setSlowedByUsingItem(false));
            }

            if (status.getStatus() == 31) {
                PacketEntity hook = player.compensatedEntities.getEntity(status.getEntityId());
                if (!(hook instanceof PacketEntityHook hookEntity)) return;

                if (hookEntity.attached == player.entityID) {
                    player.sendTransaction();
                    // We don't transaction sandwich this, it's too rare to be a real problem.
                    player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.uncertaintyHandler.fishingRodPulls.add(hookEntity.owner));
                }
            }

            if (status.getStatus() >= 24 && status.getStatus() <= 28 && status.getEntityId() == player.entityID) {
                player.compensatedEntities.self.setOpLevel(status.getStatus() - 24);
            }
        } else if (event.getPacketType() == PacketTypes.Play.Server.SET_SLOT) {
            ServerSetSlotPacket slot = packetFactory.serverSetSlot(event);

            if (slot.getWindowId() == 0) {
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
                    if (slot.getSlot() - 36 == player.packetStateData.lastSlotSelected && (player.getInventory().getHeldItem().getType() == slot.getItem().getType() || player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_8))) {
                        player.packetStateData.setSlowedByUsingItem(false);

                        if (player.isResetItemUsageOnItemUpdate()) {
                            GrimAPI.INSTANCE.getItemResetHandler().resetItemUsage(player.platformPlayer);
                        }
                    }
                });

                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> {
                    if (slot.getSlot() - 36 == player.packetStateData.lastSlotSelected && (player.getInventory().getHeldItem().getType() == slot.getItem().getType() || player.getClientVersion().isOlderThanOrEquals(PacketClientVersions.V_1_8))) {
                        player.packetStateData.setSlowedByUsingItem(false);

                        if (player.isResetItemUsageOnItemUpdate()) {
                            GrimAPI.INSTANCE.getItemResetHandler().resetItemUsage(player.platformPlayer);
                        }
                    }
                });
            }
        } else if (event.getPacketType() == PacketTypes.Play.Server.WINDOW_ITEMS) {
            ServerWindowItemsPacket items = packetFactory.serverWindowItems(event);

            if (items.getWindowId() == 0) { // Player inventory
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.packetStateData.setSlowedByUsingItem(false));
                player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> player.packetStateData.setSlowedByUsingItem(false));
            }
        }

        // 1.8 clients fail to send the RELEASE_USE_ITEM packet when a window is opened client sided while using an item
        else if (event.getPacketType() == PacketTypes.Play.Server.OPEN_WINDOW) {
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.packetStateData.setSlowedByUsingItem(false));
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> player.packetStateData.setSlowedByUsingItem(false));
        } else if (event.getPacketType() == PacketTypes.Play.Server.OPEN_HORSE_WINDOW) {
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> player.packetStateData.setSlowedByUsingItem(false));
            player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get() + 1, () -> player.packetStateData.setSlowedByUsingItem(false));
        } else if (event.getPacketType() == PacketTypes.Play.Server.SET_PASSENGERS) {
            ServerSetPassengersPacket mount = new ServerSetPassengersPacket(event);

            int vehicleID = mount.getEntityId();
            int[] passengers = mount.getPassengers();

            handleMountVehicle(event, vehicleID, passengers);
        } else if (event.getPacketType() == PacketTypes.Play.Server.ATTACH_ENTITY) {
            ServerAttachEntityPacket attach = new ServerAttachEntityPacket(event);

            // This packet was replaced by the mount packet on 1.9+ servers - to support multiple passengers on one vehicle
            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9))
                return;

            // If this is mounting rather than leashing
            if (!attach.isLeash()) {
                // Alright, let's convert this to the 1.9+ format to make it easier for grim
                int vehicleID = attach.getHoldingId();
                int attachID = attach.getAttachedId();
                TrackerData trackerData = player.compensatedEntities.getTrackedEntity(attachID);

                if (trackerData != null) {
                    // 1.8 sends a vehicle ID of -1 to dismount the entity from its vehicle
                    // This is opposite of the 1.9+ format, which sends the vehicle ID and then an empty array.
                    if (vehicleID == -1) { // Dismounting
                        vehicleID = trackerData.getLegacyPointEightMountedUpon();
                        handleMountVehicle(event, vehicleID, new int[]{}); // The vehicle is empty
                    } else { // Mounting
                        trackerData.setLegacyPointEightMountedUpon(vehicleID);
                        handleMountVehicle(event, vehicleID, new int[]{attachID});
                    }
                } else {
                    // I don't think we can recover from this... warn and move on as this shouldn't happen.
                    LogUtil.warn("Server sent an invalid attach entity packet for entity " + attach.getHoldingId() + " with passenger " + attach.getAttachedId() + "! The client ignores this.");
                }
            }
        } else if (event.getPacketType() == PacketTypes.Play.Server.DESTROY_ENTITIES) {
            ServerDestroyEntitiesPacket destroy = new ServerDestroyEntitiesPacket(event);

            int[] destroyEntityIds = destroy.getEntityIds();

            for (int entityID : destroyEntityIds) {
                despawnedEntitiesThisTransaction.add(entityID);
                player.compensatedEntities.serverPositionsMap.remove(entityID);
                // Remove the tracked vehicle (handling tracking knockback) if despawned
                if (player.compensatedEntities.serverPlayerVehicle != null && player.compensatedEntities.serverPlayerVehicle == entityID) {
                    player.compensatedEntities.serverPlayerVehicle = null;
                }
            }

            final int destroyTransaction = player.lastTransactionSent.get() + 1;
            player.latencyUtils.addRealTimeTask(destroyTransaction, () -> {
                for (int integer : destroyEntityIds) {
                    player.compensatedEntities.removeEntity(integer);
                    player.fireworks.removeFirework(integer);
                }
            });

            // Don't let the player freeze transactions to keep the firework boost velocity + uncertainty
            // Also generally prevents people with high ping gaining too high an advantage in firework use
            if (maxFireworkBoostPing > 0) {
                player.runNettyTaskInMs(() -> {
                    if (player.lastTransactionReceived.get() >= destroyTransaction) return;
                    for (int entityID : destroyEntityIds) {
                        // If the player has a firework boosting them, setback
                        if (player.fireworks.hasFirework(entityID)) {
                            player.getSetbackTeleportUtil().executeViolationSetback();
                            break;
                        }
                    }
                }, maxFireworkBoostPing);
            }
        }
    }

    private void handleMountVehicle(PacketSendEvent event, int vehicleID, int[] passengers) {
        boolean wasInVehicle = player.compensatedEntities.serverPlayerVehicle != null && player.compensatedEntities.serverPlayerVehicle == vehicleID;
        boolean inThisVehicle = false;

        for (int passenger : passengers) {
            inThisVehicle = passenger == player.entityID;
            if (inThisVehicle) break;
        }

        if (inThisVehicle && !wasInVehicle) {
            player.handleMountVehicle(vehicleID);
        }

        if (!inThisVehicle && wasInVehicle) {
            player.handleDismountVehicle(event);
        }
        // Better lag compensation if we were affected by this
        if (wasInVehicle || inThisVehicle) {
            player.sendTransaction();
        }
        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
            PacketEntity vehicle = player.compensatedEntities.getEntity(vehicleID);

            // Vanilla likes sending null vehicles, so we must ignore those like the client ignores them
            if (vehicle == null) return;

            // Eject existing passengers for this vehicle
            for (PacketEntity passenger : new ArrayList<>(vehicle.passengers)) {
                passenger.eject();
            }

            // Add the entities as vehicles
            for (int entityID : passengers) {
                PacketEntity passenger = player.compensatedEntities.getEntity(entityID);
                if (passenger == null) continue;
                passenger.mount(vehicle);
            }
        });
    }

    private void handleMoveEntity(PacketSendEvent event, int entityId, double deltaX, double deltaY, double deltaZ, Float yaw, Float pitch, boolean isRelative, boolean hasPos) {
        TrackerData data = player.compensatedEntities.getTrackedEntity(entityId);

        final boolean didNotSendPreWave = hasSentPreWavePacket.compareAndSet(false, true);
        if (didNotSendPreWave) player.sendTransaction();

        if (data != null) {
            // Update the tracked server's entity position
            if (isRelative) {
                // There is a bug where vehicles may start flying due to mojang setting packet position on the client
                // (Works at 0 ping but causes funny bugs at any higher ping)
                // As we don't want vehicles to fly, we need to replace it with a teleport if it is player vehicle
                //
                // Don't bother with client controlled vehicles though
                boolean vanillaVehicleFlight = player.compensatedEntities.serverPlayerVehicle != null
                        && player.compensatedEntities.serverPlayerVehicle == entityId
                        && player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9)
                        // TODO: https://discord.com/channels/721686193061888071/721686193515003966/1310659538831020123
                        // Why does the server now send an entity rel move packet matching the player's vehicle movement every time?
                        && PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21_2)
                        && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9);

                // ViaVersion sends two relative packets when moving more than 4 blocks
                // This is broken and causes the client to interpolate like (0, 4) and (1, 3) instead of (1, 7)
                // This causes impossible hits, so grim must replace this with a teleport entity packet
                // Not ideal, but neither is 1.8 players on a 1.9+ server.
                if (vanillaVehicleFlight ||
                        ((Math.abs(deltaX) >= 3.9375 || Math.abs(deltaY) >= 3.9375 || Math.abs(deltaZ) >= 3.9375) && player.getClientVersion().isOlderThan(PacketClientVersions.V_1_9) && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_9))) {
                    player.user.writePacket(ServerEntityTeleportPacket.from(entityId, MCPacket.getAPI().getVectorFactory().getImmutableVec3d(data.getX() + deltaX, data.getY() + deltaY, data.getZ() + deltaZ), yaw == null ? data.getXRot() : yaw, pitch == null ? data.getYRot() : pitch, false));
                    event.setCancelled(true);
                    return;
                }

                data.setX(data.getX() + deltaX);
                data.setY(data.getY() + deltaY);
                data.setZ(data.getZ() + deltaZ);
            } else {
                data.setX(deltaX);
                data.setY(deltaY);
                data.setZ(deltaZ);
            }
            if (yaw != null) {
                data.setXRot(yaw);
                data.setYRot(pitch);
            }

            // We can't hang two relative moves on one transaction
            if (data.getLastTransactionHung() == player.lastTransactionSent.get()) {
                player.sendTransaction();
            }
            data.setLastTransactionHung(player.lastTransactionSent.get());
        }

        int lastTrans = player.lastTransactionSent.get();

        player.latencyUtils.addRealTimeTask(lastTrans, () -> {
            PacketEntity entity = player.compensatedEntities.getEntity(entityId);
            if (entity == null) return;
            if (entity instanceof PacketEntityTrackXRot xRotEntity && yaw != null) {
                xRotEntity.packetYaw = yaw;
                xRotEntity.steps = entity.isBoat() ? 10 : 3;
            }

            entity.onFirstTransaction(isRelative, hasPos, deltaX, deltaY, deltaZ, player);
        });

        player.latencyUtils.addRealTimeTask(lastTrans + 1, () -> {
            PacketEntity entity = player.compensatedEntities.getEntity(entityId);
            if (entity == null) return;
            entity.onSecondTransaction();
        });
    }

    public void addEntity(int entityID, UUID uuid, PacketEntityType type, ImmutableVector3d position, float xRot, float yRot, List<EntityData<?>> entityMetadata, int extraData) {
        if (despawnedEntitiesThisTransaction.contains(entityID)) {
            player.sendTransaction();
        }

        player.compensatedEntities.serverPositionsMap.put(entityID, new TrackerData(position.getX(), position.getY(), position.getZ(), xRot, yRot, type, player.lastTransactionSent.get()));

        player.latencyUtils.addRealTimeTask(player.lastTransactionSent.get(), () -> {
            player.compensatedEntities.addEntity(entityID, uuid, type, position, xRot, extraData);
            if (entityMetadata != null) {
                player.compensatedEntities.updateEntityMetadata(entityID, entityMetadata);
            }
        });
    }

    private boolean isDirectlyAffectingPlayer(GrimPlayer player, int entityID) {
        // The attributes for this entity is active, currently
        return (player.compensatedEntities.serverPlayerVehicle == null && entityID == player.entityID) ||
                (player.compensatedEntities.serverPlayerVehicle != null && entityID == player.compensatedEntities.serverPlayerVehicle);
    }

    public void onEndOfTickEvent() {
        // Only send a transaction at the end of the tick if we are tracking players
        player.sendTransaction(true); // We injected before vanilla flushes :) we don't need to flush
    }

    public void tickStartTick() {
        hasSentPreWavePacket.set(false);
    }

    @Override
    public void onReload(ConfigManager config) {
        maxFireworkBoostPing = config.getIntElse("max-ping-firework-boost", 1000);
    }

}
