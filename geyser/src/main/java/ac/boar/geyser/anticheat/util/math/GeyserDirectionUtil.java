package ac.boar.geyser.anticheat.util.math;

import org.geysermc.geyser.level.physics.Direction;

import static org.geysermc.geyser.level.physics.Direction.EAST;
import static org.geysermc.geyser.level.physics.Direction.NORTH;
import static org.geysermc.geyser.level.physics.Direction.SOUTH;
import static org.geysermc.geyser.level.physics.Direction.WEST;

public final class GeyserDirectionUtil {
    public static Direction rotateYCounterclockwise(Direction direction) {
        return switch (direction) {
            case NORTH -> WEST;
            case SOUTH -> EAST;
            case WEST -> SOUTH;
            case EAST -> NORTH;
            default -> throw new IllegalStateException("Unable to get CCW facing of " + direction);
        };
    }

    public static Direction getClockWise(Direction direction) {
        return switch (direction.ordinal()) {
            case 2 -> EAST;
            case 5 -> SOUTH;
            case 3 -> WEST;
            case 4 -> NORTH;
            default -> throw new IllegalStateException("Unable to get Y-rotated facing of " + direction);
        };
    }
}
