package ac.boar.anticheat.util.math;

import org.cloudburstmc.math.vector.Vector3i;

public enum Direction {
    DOWN(Axis.Y, Vector3i.from(0, -1, 0)),
    UP(Axis.Y, Vector3i.UNIT_Y),
    NORTH(Axis.Z, Vector3i.from(0, 0, -1)),
    SOUTH(Axis.Z, Vector3i.UNIT_Z),
    WEST(Axis.X, Vector3i.from(-1, 0, 0)),
    EAST(Axis.X, Vector3i.UNIT_X);

    public static final Direction[] VALUES = Direction.values();
    public static final Direction[] HORIZONTAL = new Direction[] { NORTH, EAST, SOUTH, WEST };

    private final Axis axis;
    private final Vector3i unitVector;

    Direction(Axis axis, Vector3i unitVector) {
        this.axis = axis;
        this.unitVector = unitVector;
    }

    public Axis getAxis() {
        return axis;
    }

    public Vector3i getUnitVector() {
        return this.unitVector;
    }
}
