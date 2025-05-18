package ac.grim.grimac.utils.anticheat.update;

import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import ac.grim.grimac.utils.data.SetBackData;
import ac.grim.grimac.utils.data.TeleportData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public final class PositionUpdate {
    private final ImmutableVector3d from, to;
    private final boolean onGround;
    private final SetBackData setback;
    private final TeleportData teleportData;
    private boolean isTeleport;
}
