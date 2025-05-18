package ac.grim.grimac.utils.nmsutil;

import ac.grim.grimac.api.packet.entity.PacketEntityType;
import ac.grim.grimac.api.packet.entity.PacketEntityTypes;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.*;
import ac.grim.grimac.utils.math.GrimMath;

/**
 * Yeah, I know this is a bad class
 * I just can't figure out how to PR it to PacketEvents due to babies, slimes, and other irregularities
 * <p>
 * I could PR a ton of classes in order to accomplish it but then no one would use it
 * (And even if they did they would likely be breaking my license...)
 */
public final class BoundingBoxSize {

    public static float getWidth(GrimPlayer player, PacketEntity packetEntity) {
        // Turtles are the only baby animal that don't follow the * 0.5 rule
        if (packetEntity.getType() == PacketEntityTypes.TURTLE && packetEntity.isBaby) return 0.36f;
        return getWidthMinusBaby(player, packetEntity) * (packetEntity.isBaby ? 0.5f : 1f);
    }

    private static float getWidthMinusBaby(GrimPlayer player, PacketEntity packetEntity) {
        final PacketEntityType type = packetEntity.getType();
        if (PacketEntityTypes.AXOLOTL.equals(type)) {
            return 0.75f;
        } else if (PacketEntityTypes.PANDA.equals(type)) {
            return 1.3f;
        } else if (PacketEntityTypes.BAT.equals(type) || PacketEntityTypes.PARROT.equals(type) || PacketEntityTypes.COD.equals(type) || PacketEntityTypes.EVOKER_FANGS.equals(type) || PacketEntityTypes.TROPICAL_FISH.equals(type) || PacketEntityTypes.FROG.equals(type)) {
            return 0.5f;
        } else if (PacketEntityTypes.ARMADILLO.equals(type) || PacketEntityTypes.BEE.equals(type) || PacketEntityTypes.PUFFERFISH.equals(type) || PacketEntityTypes.SALMON.equals(type) || PacketEntityTypes.SNOW_GOLEM.equals(type) || PacketEntityTypes.CAVE_SPIDER.equals(type)) {
            return 0.7f;
        } else if (PacketEntityTypes.WITHER_SKELETON.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 0.7f : 0.72f;
        } else if (PacketEntityTypes.WITHER_SKULL.equals(type) || PacketEntityTypes.SHULKER_BULLET.equals(type)) {
            return 0.3125f;
        } else if (PacketEntityTypes.HOGLIN.equals(type) || PacketEntityTypes.ZOGLIN.equals(type)) {
            return 1.3964844f;
        } else if (PacketEntityTypes.SKELETON_HORSE.equals(type) || PacketEntityTypes.ZOMBIE_HORSE.equals(type) || PacketEntityTypes.HORSE.equals(type) || PacketEntityTypes.DONKEY.equals(type) || PacketEntityTypes.MULE.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 1.3964844f : 1.4f;
        } else if (PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.BOAT)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 1.375f : 1.5f;
        } else if (PacketEntityTypes.CHICKEN.equals(type) || PacketEntityTypes.ENDERMITE.equals(type) || PacketEntityTypes.SILVERFISH.equals(type) || PacketEntityTypes.VEX.equals(type) || PacketEntityTypes.TADPOLE.equals(type)) {
            return 0.4f;
        } else if (PacketEntityTypes.RABBIT.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 0.4f : 0.6f;
        } else if (PacketEntityTypes.CREAKING.equals(type) || PacketEntityTypes.STRIDER.equals(type) || PacketEntityTypes.COW.equals(type) || PacketEntityTypes.SHEEP.equals(type) || PacketEntityTypes.MOOSHROOM.equals(type) || PacketEntityTypes.PIG.equals(type) || PacketEntityTypes.LLAMA.equals(type) || PacketEntityTypes.DOLPHIN.equals(type) || PacketEntityTypes.WITHER.equals(type) || PacketEntityTypes.TRADER_LLAMA.equals(type) || PacketEntityTypes.WARDEN.equals(type) || PacketEntityTypes.GOAT.equals(type)) {
            return 0.9f;
        } else if (PacketEntityTypes.PHANTOM.equals(type)) {
            if (packetEntity instanceof PacketEntitySizeable sizeable) {
                return 0.9f + sizeable.size * 0.2f;
            }

            return 1.5f;
        } else if (packetEntity instanceof PacketEntityGuardian packetEntityGuardian) { // TODO: 2.35 * guardian?
            return packetEntityGuardian.isElder ? 1.9975f : 0.85f;
        } else if (PacketEntityTypes.END_CRYSTAL.equals(type)) {
            return 2f;
        } else if (PacketEntityTypes.ENDER_DRAGON.equals(type)) {
            return 16f;
        } else if (PacketEntityTypes.FIREBALL.equals(type)) {
            return 1f;
        } else if (PacketEntityTypes.GHAST.equals(type)) {
            return 4f;
        } else if (PacketEntityTypes.GIANT.equals(type)) {
            return 3.6f;
        } else if (PacketEntityTypes.GUARDIAN.equals(type)) {
            return 0.85f;
        } else if (PacketEntityTypes.IRON_GOLEM.equals(type)) {
            return 1.4f;
        } else if (PacketEntityTypes.MAGMA_CUBE.equals(type)) {
            if (packetEntity instanceof PacketEntitySizeable sizeable) {
                float size = sizeable.size;
                return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_20_5)
                        ? 0.52f * size : player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9)
                        ? 2.04f * (0.255f * size)
                        : 0.51000005f * size;
            }

            return 0.98f;
        } else if (PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.MINECART_ABSTRACT)) {
            return 0.98f;
        } else if (PacketEntityTypes.PLAYER.equals(type)) {
            return 0.6f;
        } else if (PacketEntityTypes.POLAR_BEAR.equals(type)) {
            return 1.4f;
        } else if (PacketEntityTypes.RAVAGER.equals(type)) {
            return 1.95f;
        } else if (PacketEntityTypes.SHULKER.equals(type)) {
            return 1f;
        } else if (PacketEntityTypes.SLIME.equals(type)) {
            if (packetEntity instanceof PacketEntitySizeable sizeable) {
                float size = sizeable.size;
                return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_20_5)
                        ? 0.52f * size : player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9)
                        ? 2.04f * (0.255f * size) : 0.51000005f * size;
            }

            return 0.3125f;
        } else if (PacketEntityTypes.SMALL_FIREBALL.equals(type)) {
            return 0.3125f;
        } else if (PacketEntityTypes.SPIDER.equals(type)) {
            return 1.4f;
        } else if (PacketEntityTypes.SQUID.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 0.8f : 0.95f;
        } else if (PacketEntityTypes.TURTLE.equals(type)) {
            return 1.2f;
        } else if (PacketEntityTypes.ALLAY.equals(type)) {
            return 0.35f;
        } else if (PacketEntityTypes.SNIFFER.equals(type)) {
            return 1.9f;
        } else if (PacketEntityTypes.CAMEL.equals(type)) {
            return 1.7f;
        } else if (PacketEntityTypes.WIND_CHARGE.equals(type)) {
            return 0.3125f;
        } else if (PacketEntityTypes.ARMOR_STAND.equals(type)) {
            return 0.5F;
        } else if (PacketEntityTypes.FALLING_BLOCK.equals(type)) {
            return 0.98F;
        } else if (PacketEntityTypes.FIREWORK_ROCKET.equals(type)) {
            return 0.25F;
        }
        return 0.6f;
    }

    public static ImmutableVector3d getRidingOffsetFromVehicle(PacketEntity entity, GrimPlayer player) {
        SimpleCollisionBox box = entity.getPossibleCollisionBoxes();
        double x = (box.maxX + box.minX) / 2d;
        double y = box.minY;
        double z = (box.maxZ + box.minZ) / 2d;

        if (entity instanceof PacketEntityTrackXRot xRotEntity) {
            // Horses desync here, and we can't do anything about it without interpolating animations.
            // Mojang just has to fix it.  I'm not attempting to fix it.
            // Striders also do the same with animations, causing a desync.
            // At least the only people using buckets are people in boats for villager transportation
            // and people trying to false the anticheat.
            if (PacketEntityTypes.isTypeInstanceOf(entity.getType(), PacketEntityTypes.BOAT)) {
                float f = 0f;
                float f1 = (float) (getPassengerRidingOffset(player, entity) - 0.35f); // hardcoded player offset

                if (!entity.passengers.isEmpty()) {
                    int i = entity.passengers.indexOf(player.compensatedEntities.self);

                    if (i == 0) {
                        f = 0.2f;
                    } else if (i == 1) {
                        f = -0.6f;
                    }
                }

                ImmutableVector3d vec3 = MCPacket.getAPI().getVectorFactory().getImmutableVec3d(f, 0d, 0d);
                vec3 = yRot(GrimMath.radians(-xRotEntity.interpYaw) - ((float) Math.PI / 2f), vec3);
                return MCPacket.getAPI().getVectorFactory().getImmutableVec3d(x + vec3.x, y + (double) f1, z + vec3.z);
            } else if (entity.getType() == PacketEntityTypes.LLAMA) {
                float f = player.trigHandler.cos(GrimMath.radians(xRotEntity.interpYaw));
                float f1 = player.trigHandler.sin(GrimMath.radians(xRotEntity.interpYaw));
                return MCPacket.getAPI().getVectorFactory().getImmutableVec3d(x + (double) (0.3f * f1), y + getPassengerRidingOffset(player, entity) - 0.35f, z + (double) (0.3f * f));
            } else if (entity.getType() == PacketEntityTypes.CHICKEN) {
                float f = player.trigHandler.sin(GrimMath.radians(xRotEntity.interpYaw));
                float f1 = player.trigHandler.cos(GrimMath.radians(xRotEntity.interpYaw));
                y = y + (getHeight(player, entity) * 0.5f);
                return MCPacket.getAPI().getVectorFactory().getImmutableVec3d(x + (double) (0.1f * f), y - 0.35f, z - (double) (0.1f * f1));
            }
        }

        return MCPacket.getAPI().getVectorFactory().getImmutableVec3d(x, y + getPassengerRidingOffset(player, entity) - 0.35f, z);
    }

    private static ImmutableVector3d yRot(float yaw, ImmutableVector3d start) {
        double cos = (float) Math.cos(yaw);
        double sin = (float) Math.sin(yaw);
        return MCPacket.getAPI().getVectorFactory().getImmutableVec3d(
                start.x * cos + start.z * sin,
                start.y,
                start.z * cos - start.x * sin
        );
    }

    public static float getHeight(GrimPlayer player, PacketEntity packetEntity) {
        // Turtles are the only baby animal that don't follow the * 0.5 rule
        if (packetEntity.getType() == PacketEntityTypes.TURTLE && packetEntity.isBaby) return 0.12f;
        return getHeightMinusBaby(player, packetEntity) * (packetEntity.isBaby ? 0.5f : 1f);
    }

    public static double getMyRidingOffset(PacketEntity packetEntity) {
        final PacketEntityType type = packetEntity.getType();
        if (PacketEntityTypes.PIGLIN.equals(type) || PacketEntityTypes.ZOMBIFIED_PIGLIN.equals(type) || PacketEntityTypes.ZOMBIE.equals(type)) {
            return packetEntity.isBaby ? -0.05 : -0.45;
        } else if (PacketEntityTypes.SKELETON.equals(type)) {
            return -0.6;
        } else if (PacketEntityTypes.ENDERMITE.equals(type) || PacketEntityTypes.SILVERFISH.equals(type)) {
            return 0.1;
        } else if (PacketEntityTypes.EVOKER.equals(type) || PacketEntityTypes.ILLUSIONER.equals(type) || PacketEntityTypes.PILLAGER.equals(type) || PacketEntityTypes.RAVAGER.equals(type) || PacketEntityTypes.VINDICATOR.equals(type) || PacketEntityTypes.WITCH.equals(type)) {
            return -0.45;
        } else if (PacketEntityTypes.PLAYER.equals(type)) {
            return -0.35;
        }

        if (PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.ABSTRACT_ANIMAL)) {
            return 0.14;
        }

        return 0;
    }

    public static double getPassengerRidingOffset(GrimPlayer player, PacketEntity packetEntity) {
        if (packetEntity instanceof PacketEntityHorse)
            return (getHeight(player, packetEntity) * 0.75) - 0.25;

        final PacketEntityType type = packetEntity.getType();
        if (PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.MINECART_ABSTRACT)) {
            return 0;
        } else if (PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.BOAT)) {
            return -0.1;
        } else if (PacketEntityTypes.HOGLIN.equals(type) || PacketEntityTypes.ZOGLIN.equals(type)) {
            return getHeight(player, packetEntity) - (packetEntity.isBaby ? 0.2 : 0.15);
        } else if (PacketEntityTypes.LLAMA.equals(type)) {
            return getHeight(player, packetEntity) * 0.67;
        } else if (PacketEntityTypes.PIGLIN.equals(type)) {
            return getHeight(player, packetEntity) * 0.92;
        } else if (PacketEntityTypes.RAVAGER.equals(type)) {
            return 2.1;
        } else if (PacketEntityTypes.SKELETON.equals(type)) {
            return (getHeight(player, packetEntity) * 0.75) - 0.1875;
        } else if (PacketEntityTypes.SPIDER.equals(type)) {
            return getHeight(player, packetEntity) * 0.5;
        } else if (PacketEntityTypes.STRIDER.equals(type)) {// depends on animation position, good luck getting it exactly, this is the best you can do though
            return getHeight(player, packetEntity) - 0.19;
        }
        return getHeight(player, packetEntity) * 0.75;
    }

    private static float getHeightMinusBaby(GrimPlayer player, PacketEntity packetEntity) {
        final PacketEntityType type = packetEntity.getType();
        if (PacketEntityTypes.ARMADILLO.equals(type)) {
            return 0.65f;
        } else if (PacketEntityTypes.AXOLOTL.equals(type)) {
            return 0.42f;
        } else if (PacketEntityTypes.BEE.equals(type) || PacketEntityTypes.DOLPHIN.equals(type) || PacketEntityTypes.ALLAY.equals(type)) {
            return 0.6f;
        } else if (PacketEntityTypes.EVOKER_FANGS.equals(type) || PacketEntityTypes.VEX.equals(type)) {
            return 0.8f;
        } else if (PacketEntityTypes.SQUID.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 0.8f : 0.95f;
        } else if (PacketEntityTypes.PARROT.equals(type) || PacketEntityTypes.BAT.equals(type) || PacketEntityTypes.PIG.equals(type) || PacketEntityTypes.SPIDER.equals(type)) {
            return 0.9f;
        } else if (PacketEntityTypes.WITHER_SKULL.equals(type) || PacketEntityTypes.SHULKER_BULLET.equals(type)) {
            return 0.3125f;
        } else if (PacketEntityTypes.BLAZE.equals(type)) {
            return 1.8f;
        } else if (PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.BOAT)) {
            // WHY DOES VIAVERSION OFFSET BOATS? THIS MAKES IT HARD TO SUPPORT, EVEN IF WE INTERPOLATE RIGHT.
            // I gave up and just exempted boats from the reach check and gave up with interpolation for collisions
            return 0.5625f;
        } else if (PacketEntityTypes.CAT.equals(type)) {
            return 0.7f;
        } else if (PacketEntityTypes.CAVE_SPIDER.equals(type)) {
            return 0.5f;
        } else if (PacketEntityTypes.FROG.equals(type)) {
            return 0.55f;
        } else if (PacketEntityTypes.CHICKEN.equals(type)) {
            return 0.7f;
        } else if (PacketEntityTypes.HOGLIN.equals(type) || PacketEntityTypes.ZOGLIN.equals(type)) {
            return 1.4f;
        } else if (PacketEntityTypes.COW.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 1.4f : 1.3f;
        } else if (PacketEntityTypes.STRIDER.equals(type)) {
            return 1.7f;
        } else if (PacketEntityTypes.CREEPER.equals(type)) {
            return 1.7f;
        } else if (PacketEntityTypes.DONKEY.equals(type)) {
            return 1.5f;
        } else if (packetEntity instanceof PacketEntityGuardian packetEntityGuardian) { // TODO: 2.35 * guardian?
            return packetEntityGuardian.isElder ? 1.9975f : 0.85f;
        } else if (PacketEntityTypes.ENDERMAN.equals(type) || PacketEntityTypes.WARDEN.equals(type)) {
            return 2.9f;
        } else if (PacketEntityTypes.ENDERMITE.equals(type) || PacketEntityTypes.COD.equals(type)) {
            return 0.3f;
        } else if (PacketEntityTypes.END_CRYSTAL.equals(type)) {
            return 2f;
        } else if (PacketEntityTypes.ENDER_DRAGON.equals(type)) {
            return 8f;
        } else if (PacketEntityTypes.FIREBALL.equals(type)) {
            return 1f;
        } else if (PacketEntityTypes.FOX.equals(type)) {
            return 0.7f;
        } else if (PacketEntityTypes.GHAST.equals(type)) {
            return 4f;
        } else if (PacketEntityTypes.GIANT.equals(type)) {
            return 12f;
        } else if (PacketEntityTypes.GUARDIAN.equals(type)) {
            return 0.85f;
        } else if (PacketEntityTypes.HORSE.equals(type)) {
            return 1.6f;
        } else if (PacketEntityTypes.IRON_GOLEM.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 2.7f : 2.9f;
        } else if (PacketEntityTypes.CREAKING.equals(type)) {
            return 2.7f;
        } else if (PacketEntityTypes.LLAMA.equals(type) || PacketEntityTypes.TRADER_LLAMA.equals(type)) {
            return 1.87f;
        } else if (PacketEntityTypes.TROPICAL_FISH.equals(type)) {
            return 0.4f;
        } else if (PacketEntityTypes.MAGMA_CUBE.equals(type)) {
            if (packetEntity instanceof PacketEntitySizeable sizeable) {
                float size = sizeable.size;
                return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_20_5)
                        ? 0.52f * size : player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9)
                        ? 2.04f * (0.255f * size)
                        : 0.51000005f * size;
            }

            return 0.7f;
        } else if (PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.MINECART_ABSTRACT)) {
            return 0.7f;
        } else if (PacketEntityTypes.MULE.equals(type)) {
            return 1.6f;
        } else if (PacketEntityTypes.MOOSHROOM.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 1.4f : 1.3f;
        } else if (PacketEntityTypes.OCELOT.equals(type)) {
            return 0.7f;
        } else if (PacketEntityTypes.PANDA.equals(type)) {
            return 1.25f;
        } else if (PacketEntityTypes.PHANTOM.equals(type)) {
            if (packetEntity instanceof PacketEntitySizeable sizeable) {
                return 0.5f + sizeable.size * 0.1f;
            }

            return 1.8f;
        } else if (PacketEntityTypes.PLAYER.equals(type)) {
            return 1.8f;
        } else if (PacketEntityTypes.POLAR_BEAR.equals(type)) {
            return 1.4f;
        } else if (PacketEntityTypes.PUFFERFISH.equals(type)) {
            return 0.7f;
        } else if (PacketEntityTypes.RABBIT.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 0.5f : 0.7f;
        } else if (PacketEntityTypes.RAVAGER.equals(type)) {
            return 2.2f;
        } else if (PacketEntityTypes.SALMON.equals(type)) {
            return 0.4f;
        } else if (PacketEntityTypes.SHEEP.equals(type) || PacketEntityTypes.GOAT.equals(type)) {
            return 1.3f;
        } else if (PacketEntityTypes.SHULKER.equals(type)) { // Could maybe guess peek size, although seems useless
            return 2f;
        } else if (PacketEntityTypes.SILVERFISH.equals(type)) {
            return 0.3f;
        } else if (PacketEntityTypes.SKELETON.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 1.99f : 1.95f;
        } else if (PacketEntityTypes.SKELETON_HORSE.equals(type)) {
            return 1.6f;
        } else if (PacketEntityTypes.SLIME.equals(type)) {
            if (packetEntity instanceof PacketEntitySizeable sizeable) {
                float size = sizeable.size;
                return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_20_5)
                        ? 0.52f * size : player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9)
                        ? 2.04f * (0.255f * size)
                        : 0.51000005f * size;
            }

            return 0.3125f;
        } else if (PacketEntityTypes.SMALL_FIREBALL.equals(type)) {
            return 0.3125f;
        } else if (PacketEntityTypes.SNOW_GOLEM.equals(type)) {
            return 1.9f;
        } else if (PacketEntityTypes.STRAY.equals(type)) {
            return 1.99f;
        } else if (PacketEntityTypes.TURTLE.equals(type)) {
            return 0.4f;
        } else if (PacketEntityTypes.WITHER.equals(type)) {
            return 3.5f;
        } else if (PacketEntityTypes.WITHER_SKELETON.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 2.4f : 2.535f;
        } else if (PacketEntityTypes.WOLF.equals(type)) {
            return player.getClientVersion().isNewerThanOrEquals(PacketClientVersions.V_1_9) ? 0.85f : 0.8f;
        } else if (PacketEntityTypes.ZOMBIE_HORSE.equals(type)) {
            return 1.6f;
        } else if (PacketEntityTypes.TADPOLE.equals(type)) {
            return 0.3f;
        } else if (PacketEntityTypes.SNIFFER.equals(type)) {
            return 1.75f;
        } else if (PacketEntityTypes.CAMEL.equals(type)) {
            return 2.375f;
        } else if (PacketEntityTypes.BREEZE.equals(type)) {
            return 1.77f;
        } else if (PacketEntityTypes.BOGGED.equals(type)) {
            return 1.99f;
        } else if (PacketEntityTypes.WIND_CHARGE.equals(type)) {
            return 0.3125f;
        } else if (PacketEntityTypes.ARMOR_STAND.equals(type)) {
            return 1.975F;
        } else if (PacketEntityTypes.FALLING_BLOCK.equals(type)) {
            return 0.98F;
        } else if (PacketEntityTypes.VILLAGER.equals(type) && player.getClientVersion().isOlderThan(PacketClientVersions.V_1_9)) {
            return 1.8F;
        } else if (PacketEntityTypes.FIREWORK_ROCKET.equals(type)) {
            return 0.25F;
        }
        return 1.95f;
    }
}
