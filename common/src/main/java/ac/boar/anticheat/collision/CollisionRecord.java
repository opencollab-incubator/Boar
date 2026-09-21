package ac.boar.anticheat.collision;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.util.math.Box;
import org.cloudburstmc.math.vector.Vector3i;

public record CollisionRecord(Box shape, BoarBlockState blockState, Vector3i sourcePosition) {
}
