package ac.boar.anticheat.util.math;

import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3i;

@Getter
@Setter
public final class Mutable {
    private int x, y, z;

    public Mutable(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Mutable(float x, float y, float z) {
        this.x = GenericMath.floor(x);
        this.y = GenericMath.floor(y);
        this.z = GenericMath.floor(z);
    }

    public Mutable() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    public Mutable add(int x, int y, int z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }

    public Mutable add(Vector3i vector3i) {
        return add(vector3i.getX(), vector3i.getY(), vector3i.getZ());
    }

    public Mutable set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Mutable set(Vector3i vector3i) {
        return set(vector3i.getX(), vector3i.getY(), vector3i.getZ());
    }

    public Mutable set(Vector3i vector3i, Vector3i vector31) {
        return set(vector3i.getX() + vector31.getX(), vector3i.getY() + vector31.getY(), vector3i.getZ() + vector31.getZ());
    }
}