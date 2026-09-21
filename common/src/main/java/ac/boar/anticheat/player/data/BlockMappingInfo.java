package ac.boar.anticheat.player.data;

import ac.boar.mappings.block.FallingFlowMaterial;
import it.unimi.dsi.fastutil.ints.IntList;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;

import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * Holds very simple information related to block mappings. A few convenience entries exist
 * to access common block states, with the {@code toIntermediary} being used to convert a Bedrock
 * block runtime id into the intermediary IDs used by the server.
 *
 * @param airDefinition the air definition
 * @param waterDefinition the water definition
 * @param lavaDefinition the lava definition
 * @param powderSnowDefinition the powder snow definition
 * @param airIds the blocks considered air
 * @param itemFramePredicate the item-frame block check
 * @param toIntermediary the intermediary mapper
 * @param fromIntermediary the intermediary mapper
 * @param fallingFlowMaterial the ordinary-layer runtime ID to falling-flow material lookup
 */
public record BlockMappingInfo(
        BlockDefinition airDefinition,
        BlockDefinition waterDefinition,
        BlockDefinition lavaDefinition,
        BlockDefinition powderSnowDefinition,
        IntList airIds,
        Predicate<BlockDefinition> itemFramePredicate,
        IntUnaryOperator toIntermediary,
        IntUnaryOperator fromIntermediary,
        IntFunction<FallingFlowMaterial> fallingFlowMaterial
) {

    // Adapters without a native material lookup retain the current fluid behavior.
    public BlockMappingInfo(
            BlockDefinition airDefinition,
            BlockDefinition waterDefinition,
            BlockDefinition lavaDefinition,
            BlockDefinition powderSnowDefinition,
            IntList airIds,
            Predicate<BlockDefinition> itemFramePredicate,
            IntUnaryOperator toIntermediary,
            IntUnaryOperator fromIntermediary
    ) {
        this(airDefinition, waterDefinition, lavaDefinition, powderSnowDefinition, airIds,
                itemFramePredicate, toIntermediary, fromIntermediary,
                runtimeId -> FallingFlowMaterial.NOT_SEARCHED);
    }

    public int airId() {
        return this.airDefinition.getRuntimeId();
    }

    public int waterId() {
        return this.waterDefinition.getRuntimeId();
    }

    public boolean isItemFrame(final BlockDefinition definition) {
        return definition != null && this.itemFramePredicate.test(definition);
    }
}
