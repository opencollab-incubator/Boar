package ac.boar.anticheat.player.data;

import ac.boar.mappings.block.FallingFlowMaterial;
import ac.boar.mappings.block.HorseJumpMaterial;
import it.unimi.dsi.fastutil.ints.IntList;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;
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
 * @param horseJumpMaterial the runtime ID to horse jump material lookup (honey, carpet); UNKNOWN for every ID when the adapter has none
 * @param rawIdDefined whether the player's block palette defines a runtime ID; true for every ID when the adapter cannot tell (see isKnownRawId)
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
        IntFunction<FallingFlowMaterial> fallingFlowMaterial,
        IntFunction<HorseJumpMaterial> horseJumpMaterial,
        IntPredicate rawIdDefined
) {

    // Adapters with a horse jump material lookup: an ID it knows is an ID the palette defines.
    public BlockMappingInfo(
            BlockDefinition airDefinition,
            BlockDefinition waterDefinition,
            BlockDefinition lavaDefinition,
            BlockDefinition powderSnowDefinition,
            IntList airIds,
            Predicate<BlockDefinition> itemFramePredicate,
            IntUnaryOperator toIntermediary,
            IntUnaryOperator fromIntermediary,
            IntFunction<FallingFlowMaterial> fallingFlowMaterial,
            IntFunction<HorseJumpMaterial> horseJumpMaterial
    ) {
        this(airDefinition, waterDefinition, lavaDefinition, powderSnowDefinition, airIds,
                itemFramePredicate, toIntermediary, fromIntermediary,
                fallingFlowMaterial, horseJumpMaterial, runtimeId -> {
                    final HorseJumpMaterial material = horseJumpMaterial.apply(runtimeId);
                    return material != null && material.known;
                });
    }

    public BlockMappingInfo(
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
        this(airDefinition, waterDefinition, lavaDefinition, powderSnowDefinition, airIds,
                itemFramePredicate, toIntermediary, fromIntermediary,
                fallingFlowMaterial, runtimeId -> HorseJumpMaterial.UNKNOWN, runtimeId -> true);
    }

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

    /**
     * Can Boar trust what this runtime ID means for collision? False when the palette does not define it (rawIdDefined), or when the
     * collision mapping turns a non-air ID into air: the mapping could not resolve it (Geyser maps an unknown Bedrock ID to Java air).
     * An unknown ID is never treated as air. Used by KnownBlockReads, the vehicle exit starts, the static seat exits and HorseJumpBlocks.
     */
    public boolean isKnownRawId(final int runtimeId) {
        if (!this.rawIdDefined.test(runtimeId)) {
            return false;
        }
        return this.airIds.contains(runtimeId) || !this.airIds.contains(this.fromIntermediary.applyAsInt(this.toIntermediary.applyAsInt(runtimeId)));
    }

    public boolean isItemFrame(final BlockDefinition definition) {
        return definition != null && this.itemFramePredicate.test(definition);
    }
}
