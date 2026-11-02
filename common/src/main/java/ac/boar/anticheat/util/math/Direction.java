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

    public static Direction fromInt(int axis) {
        if (axis < 0 || axis > 5) {
            throw new IllegalArgumentException("face range must be between 0 and 5, got " + axis);
        }
        return VALUES[axis];
    }

    public Axis getAxis() {
        return axis;
    }

    public Vector3i getUnitVector() {
        return this.unitVector;
    }

    public Direction getOpposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

}
