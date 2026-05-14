package ac.boar.geyser.anticheat.data.block;

import ac.boar.anticheat.data.block.BlockStateFactory;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.block.BoarBlockStateDelegate;
import ac.boar.anticheat.data.block.VanillaBoarBlockState;
import ac.boar.anticheat.data.block.impl.BedBlockState;
import ac.boar.anticheat.data.block.impl.HoneyBlockState;
import ac.boar.anticheat.data.block.impl.SlimeBlockState;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.type.BlockState;

public class GeyserBlockStateFactory implements BlockStateFactory {
    private static final GeyserBlockStateFactory INSTANCE = new GeyserBlockStateFactory();

    @Override
    public BoarBlockState create(int blockId, Vector3i pos, int layer) {
        BlockState state = BlockState.of(blockId);
        BoarBlockStateDelegate delegate = new GeyserBoarBlockStateDelegate(state, pos, layer);

        if (state.is(Blocks.HONEY_BLOCK)) {
            return new HoneyBlockState(delegate);
        } else if (state.is(Blocks.SLIME_BLOCK)) {
            return new SlimeBlockState(delegate);
        } else if (state.block().toString().contains("_bed")) { // nasty hack, but works!
            return new BedBlockState(delegate);
        }

        return new VanillaBoarBlockState(delegate);
    }

    public static GeyserBlockStateFactory get() {
        return INSTANCE;
    }
}
