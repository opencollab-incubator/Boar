package ac.boar.anticheat.util.math;

import org.cloudburstmc.math.GenericMath;

public final class VoxelRayTrace {
    private VoxelRayTrace() {
    }

    public interface BlockVisitor {
        boolean visit(int x, int y, int z);
    }

    public static void blocksBetween(final Vec3 start, final Vec3 end, final int maxSteps, final BlockVisitor visitor) {
        final float dx = end.x - start.x;
        final float dy = end.y - start.y;
        final float dz = end.z - start.z;
        final float lengthSq = dx * dx + dy * dy + dz * dz;
        if (lengthSq <= 1.0E-10F) {
            return;
        }

        final float radius = (float) Math.sqrt(lengthSq);
        final float dirX = dx / radius;
        final float dirY = dy / radius;
        final float dirZ = dz / radius;

        final int stepX = (int) Math.signum(dirX);
        final int stepY = (int) Math.signum(dirY);
        final int stepZ = (int) Math.signum(dirZ);

        float tMaxX = distanceToBoundary(start.x, dirX);
        float tMaxY = distanceToBoundary(start.y, dirY);
        float tMaxZ = distanceToBoundary(start.z, dirZ);

        final float tDeltaX = dirX == 0 ? 0 : stepX / dirX;
        final float tDeltaY = dirY == 0 ? 0 : stepY / dirY;
        final float tDeltaZ = dirZ == 0 ? 0 : stepZ / dirZ;

        int x = GenericMath.floor(start.x);
        int y = GenericMath.floor(start.y);
        int z = GenericMath.floor(start.z);

        for (int i = 0; i < maxSteps; i++) {
            if (!visitor.visit(x, y, z)) {
                return;
            }

            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > radius) {
                    return;
                }
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > radius) {
                    return;
                }
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > radius) {
                    return;
                }
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
    }

    private static float distanceToBoundary(float s, float ds) {
        if (ds == 0) {
            return Float.MAX_VALUE;
        }
        if (ds < 0) {
            s = -s;
            ds = -ds;
            if (GenericMath.floor(s) == s) {
                return 0;
            }
        }
        return (1 - (s - GenericMath.floor(s))) / ds;
    }
}
