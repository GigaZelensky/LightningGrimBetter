package ac.grim.grimac.utils.data.packetentity;

import ac.grim.grimac.api.packet.entity.PacketEntityType;
import ac.grim.grimac.api.packet.entity.PacketEntityTypes;
import ac.grim.grimac.api.packet.protocol.PacketClientVersion;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.api.packet.protocol.PacketClientVersions;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.attribute.ValuedAttribute;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;

import java.util.UUID;

public class PacketEntityHorse extends PacketEntityTrackXRot {

    public boolean isRearing = false;
    public boolean hasSaddle = false;
    public boolean isTame = false;

    public PacketEntityHorse(GrimPlayer player, UUID uuid, PacketEntityType type, double x, double y, double z, float xRot) {
        super(player, uuid, type, x, y, z, xRot);
        setAttribute(Attributes.STEP_HEIGHT, 1.0f);

        final boolean preAttribute = player.getClientVersion().isOlderThan(PacketClientVersions.V_1_20_5);
        // This was horse.jump_strength pre-attribute
        trackAttribute(ValuedAttribute.ranged(Attributes.JUMP_STRENGTH, 0.7, 0, preAttribute ? 2 : 32)
                .withSetRewriter((oldValue, newValue) -> {
                    // Seems viabackwards doesn't rewrite this (?)
                    if (preAttribute && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_20_5)) {
                        return oldValue;
                    }
                    // Modern player OR an old server setting legacy horse.jump_strength attribute
                    return newValue;
                }));
        trackAttribute(ValuedAttribute.ranged(Attributes.MOVEMENT_SPEED, 0.225f, 0, 1024));

        if (PacketEntityTypes.isTypeInstanceOf(type, PacketEntityTypes.CHESTED_HORSE)) {
            setAttribute(Attributes.JUMP_STRENGTH, 0.5);
            setAttribute(Attributes.MOVEMENT_SPEED, 0.175f);
        }

        if (type == PacketEntityTypes.ZOMBIE_HORSE || type == PacketEntityTypes.SKELETON_HORSE) {
            setAttribute(Attributes.MOVEMENT_SPEED, 0.2f);
        }
    }
}
