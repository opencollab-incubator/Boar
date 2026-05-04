package ac.boar.mappings.block;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.util.Referenced;
import org.cloudburstmc.math.vector.Vector3i;

public interface Block extends Referenced<Block> {

    float destroyTime();

    BoarBlockState defaultBlockState(Vector3i position, int layer);
}
