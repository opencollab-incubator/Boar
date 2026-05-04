package ac.boar.anticheat.data.block;

import org.cloudburstmc.math.vector.Vector3i;

public interface BlockStateFactory {

    BoarBlockState create(int blockId, Vector3i pos, int layer);
}
