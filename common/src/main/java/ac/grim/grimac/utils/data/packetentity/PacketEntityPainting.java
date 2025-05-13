package ac.grim.grimac.utils.data.packetentity;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.api.packet.entity.PacketEntityTypes;
import com.github.retrooper.packetevents.protocol.world.Direction;
import lombok.Getter;

import java.util.UUID;

@Getter
public class PacketEntityPainting extends PacketEntity {

    private final Direction direction;

    public PacketEntityPainting(GrimPlayer player, UUID uuid, double x, double y, double z, Direction direction) {
        super(player, uuid, PacketEntityTypes.PAINTING, x, y, z);
        this.direction = direction;
    }
}
