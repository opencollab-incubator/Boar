package ac.boar.geyser.anticheat.data.block;

import ac.boar.anticheat.data.block.BlockStateFactory;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.geyser.anticheat.data.block.impl.BedBlockState;
import ac.boar.geyser.anticheat.data.block.impl.HoneyBlockState;
import ac.boar.geyser.anticheat.data.block.impl.SlimeBlockState;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.type.BlockState;

public class GeyserBlockStateFactory implements BlockStateFactory {
    private static final GeyserBlockStateFactory INSTANCE = new GeyserBlockStateFactory();

    @Override
    public BoarBlockState create(int blockId, Vector3i pos, int layer) {
        BlockState state = BlockState.of(blockId);
        if (state.is(Blocks.HONEY_BLOCK)) {
            return new HoneyBlockState(state, pos, layer);
        } else if (state.is(Blocks.SLIME_BLOCK)) {
            return new SlimeBlockState(state, pos, layer);
        } else if (state.block().toString().contains("_bed")) { // nasty hack, but works!
            return new BedBlockState(state, pos, layer);
        }

        return new GeyserBoarBlockState(state, pos, layer);
    }

    public static GeyserBlockStateFactory get() {
        return INSTANCE;
    }
}
