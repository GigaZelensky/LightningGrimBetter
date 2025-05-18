package ac.grim.grimac.utils.data;

import ac.grim.grimac.api.math.Vector3dm;
import ac.grim.grimac.api.packet.MCPacket;
import ac.grim.grimac.api.packet.block.PacketBlockState;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import ac.grim.grimac.api.packet.world.enums.BlockFace;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3i;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class HitData {
    ImmutableVector3i position;
    Vector3dm blockHitLocation;
    PacketBlockState state;
    BlockFace closestDirection;

    public HitData(ImmutableVector3i position, Vector3dm blockHitLocation, BlockFace closestDirection, PacketBlockState state) {
        this.position = position;
        this.blockHitLocation = blockHitLocation;
        this.closestDirection = closestDirection;
        this.state = state;
    }

    public ImmutableVector3d getRelativeBlockHitLocation() {
        return MCPacket.getAPI().getVectorFactory().getImmutableVec3d(blockHitLocation.getX() - position.getX(), blockHitLocation.getY() - position.getY(), blockHitLocation.getZ() - position.getZ());
    }
}
