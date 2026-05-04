package ac.boar.anticheat.util.math;

import lombok.Getter;
import lombok.ToString;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;

@ToString
@Getter
public class Vec3 implements Cloneable {
    public final static Vec3 ZERO = new Vec3(0, 0, 0);

    public float x, y, z;

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vector3f vector3f) {
        this.x = vector3f.getX();
        this.y = vector3f.getY();
        this.z = vector3f.getZ();
    }

    public Vec3(Vector3i vector3i) {
        this.x = vector3i.getX();
        this.y = vector3i.getY();
        this.z = vector3i.getZ();
    }

    public int compareTo(Vector3i vec3i) {
        if (this.getY() == vec3i.getY()) {
            return (int) (this.getZ() == vec3i.getZ() ? this.getX() - vec3i.getX() : this.getZ() - vec3i.getZ());
        } else {
            return (int) (this.getY() - vec3i.getY());
        }
    }

    public float distToCenterSqr(Vec3 vec3) {
        return distToCenterSqr(vec3.getX(), vec3.getY(), vec3.getZ());
    }

    public float distToCenterSqr(float d, float e, float f) {
        float g = this.getX() + 0.5F - d;
        float h = this.getY() + 0.5F - e;
        float i = this.getZ() + 0.5F - f;
        return g * g + h * h + i * i;
    }

    public Vector3f toVector3f() {
        return Vector3f.from(this.x, this.y, this.z);
    }

    public Vector3i toVector3i() {
        return Vector3i.from(GenericMath.floor(this.x), GenericMath.floor(this.y), GenericMath.floor(this.z));
    }

    public Vector3d toVector3d() {
        return Vector3d.from(this.x, this.y, this.z);
    }

    public float squaredDistanceTo(Vec3 vec) {
        float d = vec.x - this.x;
        float e = vec.y - this.y;
        float f = vec.z - this.z;
        return d * d + e * e + f * f;
    }

    public float distanceTo(Vec3 vec) {
        return (float) Math.sqrt(squaredDistanceTo(vec));
    }

    public float horizontalLength() {
        return (float) Math.sqrt(horizontalLengthSquared());
    }

    public float horizontalLengthSquared() {
        return this.x * this.x + this.z * this.z;
    }

    public float lengthSquared() {
        return this.getX() * this.getX() + this.getY() * this.getY() + this.getZ() * this.getZ();
    }

    public float length() {
        return (float) Math.sqrt(this.lengthSquared());
    }

    public Vec3 add(float v) {
        return this.add(v, v, v);
    }

    public Vec3 add(Vec3 vec3) {
        return this.add(vec3.x, vec3.y, vec3.z);
    }

    public Vec3 add(float v, float v1, float v2) {
        return new Vec3(this.x + v, this.y + v1, this.z + v2);
    }

    public Vec3 subtract(Vec3 v) {
        return this.subtract(v.getX(), v.getY(), v.getZ());
    }

    public Vec3 subtract(float v, float v1, float v2) {
        return new Vec3(this.x - v, this.y - v1, this.z - v2);
    }

    public Vec3 multiply(float a) {
        return this.multiply(a, a, a);
    }

    public Vec3 multiply(float v, float v1, float v2) {
        return new Vec3(this.x * v, this.y * v1, this.z * v2);
    }

    public Vec3 multiply(Vec3 v) {
        return this.multiply(v.getX(), v.getY(), v.getZ());
    }

    public Vec3 divide(float v) {
        return this.divide(v, v, v);
    }

    public Vec3 divide(float v, float v1, float v2) {
        return new Vec3(this.x * v, this.y * v1, this.z * v2);
    }

    public Vec3 up(float v) {
        return new Vec3(this.getX(), this.getY() + v, this.getZ());
    }

    public Vec3 down(float v) {
        return new Vec3(this.getX(), this.getY() - v, this.getZ());
    }

    public Vec3 north(float v) {
        return new Vec3(this.getX(), this.getY(), this.getZ() - v);
    }

    public Vec3 south(float v) {
        return new Vec3(this.getX(), this.getY(), this.getZ() + v);
    }

    public Vec3 east(float v) {
        return new Vec3(this.getX() + v, this.getY(), this.getZ());
    }

    public Vec3 west(float v) {
        return new Vec3(this.getX() - v, this.getY(), this.getZ());
    }

    public Vec3 normalize() {
        float length = this.length();
        if (Math.abs(length) < GenericMath.FLT_EPSILON) {
            return Vec3.ZERO;
        } else {
            return new Vec3(this.getX() / length, this.getY() / length, this.getZ() / length);
        }
    }

    public String horizontalToString() {
        return "(" + this.x + ", " + this.z + ")";
    }

    public Vec3 clone() {
        return new Vec3(this.x, this.y, this.z);
    }
}