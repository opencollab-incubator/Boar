package ac.boar.anticheat.util;

import ac.boar.anticheat.util.math.Vec3;

import org.cloudburstmc.math.TrigMath;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;

public class MathUtil {
    public final static float DEGREE_TO_RAD = 0.017453292F;

    public static boolean sameDirection(Vec3 vec3, Vec3 vec32) {
        return sign(vec3.x) == sign(vec32.x) && sign(vec3.y) == sign(vec32.y) && sign(vec3.z) == sign(vec32.z);
    }

    public static boolean sameDirectionHorizontal(Vec3 vec3, Vec3 vec32) {
        return sign(vec3.x) == sign(vec32.x) && sign(vec3.z) == sign(vec32.z);
    }

    public static Vec3 signAll(Vec3 vec3) {
        return new Vec3(sign(vec3.x), sign(vec3.y), sign(vec3.z));
    }

    public static int sign(final float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0;
        }

        return value == 0 ? 0 : value > 0 ? 1 : -1;
    }

    public static float wrapDegrees(float f) {
        float g = f % 360.0f;
        if (g >= 180.0f) {
            g -= 360.0f;
        }
        if (g < -180.0f) {
            g += 360.0f;
        }
        return g;
    }

    public static boolean equal(float d, float e) {
        return Math.abs(e - d) < 1.0E-7F;
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static float square(float v) {
        return v * v;
    }

    public static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0;
        }

        return value < min ? min : Math.min(value, max);
    }

    public static boolean compare(final Vector3i vector3i, final Vector3i vector3i1) {
        return vector3i != null && vector3i1 != null && vector3i.getX() == vector3i1.getX() && vector3i.getY() == vector3i1.getY() && vector3i.getZ() == vector3i1.getZ();
    }

    public static boolean isValid(final Vector3i vector3i) {
        return Float.isFinite(vector3i.getX()) && Float.isFinite(vector3i.getY()) &&
                Float.isFinite(vector3i.getZ());
    }

    public static boolean isValid(final Vector3f vector3i) {
        return Float.isFinite(vector3i.getX()) && Float.isFinite(vector3i.getY()) &&
                Float.isFinite(vector3i.getZ());
    }

    public static Vec3 getRotationVector(float pitch, float yaw) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = TrigMath.cos(g);
        float i = TrigMath.sin(g);
        float j = TrigMath.cos(f);
        float k = TrigMath.sin(f);
        return new Vec3(i * j, -k, h * j);
    }

    public static Vec3 getInputVector(final Vec3 movementInput, float speed, float yaw) {
        float d = movementInput.lengthSquared();
        if (d < 1.0E-7) {
            return Vec3.ZERO;
        } else {
            Vec3 lv = (d > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
            float h = TrigMath.sin(yaw * DEGREE_TO_RAD);
            float i = TrigMath.cos(yaw * DEGREE_TO_RAD);
            return new Vec3(lv.x * i - lv.z * h, lv.y, lv.z * i + lv.x * h);
        }
    }

    public static int blockPosition(int x, int y, int z) {
        return (x << 8) | (z << 4) | y;
    }

    // Surprisingly, Geyser messed up here, MathUtils#chunkPositionToLong is doing ((x & 0xFFFFFFFFL) << 32L) | (z & 0xFFFFFFFFL) instead which while works
    // doesn't match vanilla behaviour, we should be doing the reverse.
    public static long chunkPositionToLong(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32L);
    }
}
