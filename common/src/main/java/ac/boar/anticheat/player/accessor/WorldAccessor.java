package ac.boar.anticheat.player.accessor;

import ac.boar.anticheat.data.block.BoarBlockState;
import org.cloudburstmc.math.vector.Vector3i;

/**
 * Accessor for accessing world data from the server.
 */
public interface WorldAccessor {

    BoarBlockState blockStateAt(Vector3i position, int layer);

    default boolean isItemFrameAt(Vector3i position) {
        return false;
    }
}
