package ac.grim.grimac.utils.data;

import ac.grim.grimac.api.packet.MCPacket;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import lombok.Getter;
import lombok.Setter;

@Getter
public final class TrackedPosition {

    private static final double MODERN_COORDINATE_SCALE = 4096.0;
    private static final double LEGACY_COORDINATE_SCALE = 32.0;

    private final double scale;
    @Setter
    private ImmutableVector3d pos = MCPacket.getAPI().getVectorFactory().getImmutableVec3d();

    public TrackedPosition() {
//        this.scale = player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9) ? MODERN_COORDINATE_SCALE : LEGACY_COORDINATE_SCALE;
        this.scale = MODERN_COORDINATE_SCALE;
    }

    public static long pack(double value, double scale) {
        return Math.round(value * scale);
    }

    public static double packLegacy(double value, double scale) {
        return Math.floor(value * scale);
    }

    private double unpack(long value) {
        return (double) value / scale;
    }

    private double unpackLegacy(double value) {
        return value / scale;
    }

    // Method since 1.16.
    public ImmutableVector3d withDelta(long x, long y, long z) {
        if (x == 0L && y == 0L && z == 0L) {
            return this.pos;
        }

        double d = x == 0L ? this.pos.getX() : unpack(pack(this.pos.getX(), scale) + x);
        double e = y == 0L ? this.pos.getY() : unpack(pack(this.pos.getY(), scale) + y);
        double f = z == 0L ? this.pos.getZ() : unpack(pack(this.pos.getZ(), scale) + z);
        return MCPacket.getAPI().getVectorFactory().getImmutableVec3d(d, e, f);
    }

    // In 1.16-, this was different.
    public ImmutableVector3d withDeltaLegacy(double x, double y, double z) {
        double d = unpackLegacy(packLegacy(this.pos.getX(), scale) + x);
        double e = unpackLegacy(packLegacy(this.pos.getY(), scale) + y);
        double f = unpackLegacy(packLegacy(this.pos.getZ(), scale) + z);
        return MCPacket.getAPI().getVectorFactory().getImmutableVec3d(d, e, f);
    }
}
