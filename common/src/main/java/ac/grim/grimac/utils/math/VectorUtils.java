package ac.grim.grimac.utils.math;

import ac.grim.grimac.api.math.Vector3dm;
import ac.grim.grimac.api.packet.util.vec.ImmutableVector3d;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;

public class VectorUtils {
    public static Vector3dm cutBoxToVector(Vector3dm vectorToCutTo, Vector3dm min, Vector3dm max) {
        SimpleCollisionBox box = new SimpleCollisionBox(min, max).sort();
        return cutBoxToVector(vectorToCutTo, box);
    }

    public static Vector3dm cutBoxToVector(Vector3dm vectorCutTo, SimpleCollisionBox box) {
        return new Vector3dm(GrimMath.clamp(vectorCutTo.getX(), box.minX, box.maxX),
                GrimMath.clamp(vectorCutTo.getY(), box.minY, box.maxY),
                GrimMath.clamp(vectorCutTo.getZ(), box.minZ, box.maxZ));
    }

    public static Vector3dm fromVec3d(ImmutableVector3d vector3d) {
        return new Vector3dm(vector3d.getX(), vector3d.getY(), vector3d.getZ());
    }

    // Clamping stops the player from causing an integer overflow and crashing the netty thread
    public static ImmutableVector3d clampVector(ImmutableVector3d toClamp) {
        double x = GrimMath.clamp(toClamp.getX(), -3.0E7D, 3.0E7D);
        double y = GrimMath.clamp(toClamp.getY(), -2.0E7D, 2.0E7D);
        double z = GrimMath.clamp(toClamp.getZ(), -3.0E7D, 3.0E7D);

        return MCPacket.getAPI().getVectorFactory().getImmutableVec3d(x, y, z);
    }
}
