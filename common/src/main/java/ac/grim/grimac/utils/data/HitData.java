package ac.grim.grimac.utils.data;

import ac.grim.grimac.api.math.Vector3dm;
import ac.grim.grimac.api.packet.block.PacketBlockState;
import ac.grim.grimac.api.packet.world.enums.BlockFace;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3i;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class HitData {
    Vector3i position;
    Vector3dm blockHitLocation;
    PacketBlockState state;
    BlockFace closestDirection;

    public HitData(Vector3i position, Vector3dm blockHitLocation, BlockFace closestDirection, PacketBlockState state) {
        this.position = position;
        this.blockHitLocation = blockHitLocation;
        this.closestDirection = closestDirection;
        this.state = state;
    }

    public Vector3d getRelativeBlockHitLocation() {
        return new Vector3d(blockHitLocation.getX() - position.getX(), blockHitLocation.getY() - position.getY(), blockHitLocation.getZ() - position.getZ());
    }
}
