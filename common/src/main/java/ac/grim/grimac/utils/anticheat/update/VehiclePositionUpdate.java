package ac.grim.grimac.utils.anticheat.update;

import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class VehiclePositionUpdate {
    private final ImmutableVector3d from, to;
    private final float xRot, yRot;
    private final boolean isTeleport;
}
