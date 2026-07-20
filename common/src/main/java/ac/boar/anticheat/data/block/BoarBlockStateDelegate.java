package ac.boar.anticheat.data.block;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Box;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.Property;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;

import java.util.List;

// Platform-specific portion of a block state. Holds the underlying platform representation (e.g. Geyser's BlockState) and answers identity/property/collision questions.
// Vanilla physics lives in AbstractBoarBlockState and is computed from these answers.
public interface BoarBlockStateDelegate {

    int intermediaryId();

    Block block();

    BlockDefinition definition(BoarPlayer player);

    <T extends Comparable<T>> BoarBlockState with(Property<T> property, T value);

    boolean isWaterlogged();

    boolean is(Block block);

    <T extends Comparable<T>> T get(Property<T> property);

    List<Box> getCollisionBoxes();

    boolean isFaceSturdy(BoarPlayer player);

    Vector3i getPosition();

    int getLayer();

    // Bedrock shapes that cannot round-trip through a Java block state (thin bars, walls): built
    // straight from the neighbours into local boxes. Returns null when the block has no override and
    // the applyConnectionShape path should be used instead.
    default List<Box> connectionCollisionOverride(BoarPlayer player, Vector3i pos) {
        return null;
    }

    // Returns a reshaped state with connection properties applied (fences, iron bars, chests, stairs).
    // Returns self when no reshape is needed.
    BoarBlockState applyConnectionShape(BoarPlayer player, BoarBlockState self, Vector3i pos);
}
