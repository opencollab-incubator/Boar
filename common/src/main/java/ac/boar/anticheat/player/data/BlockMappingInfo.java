package ac.boar.anticheat.player.data;

import it.unimi.dsi.fastutil.ints.IntList;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;

import java.util.function.IntUnaryOperator;

/**
 * Holds very simple information related to block mappings. A few convenience entries exist
 * to access common block states, with the {@code toIntermediary) being used to convert a Bedrock
 * block runtime id into the intermediary IDs used by the server.
 *
 * @param airDefinition the air definition
 * @param waterDefinition the water definition
 * @param lavaDefinition the lava definition
 * @param powderSnowDefinition the powder snow definition
 * @param airIds the blocks considered air
 * @param toIntermediary the intermediary mapper
 * @param fromIntermediary the intermediary mapper
 */
public record BlockMappingInfo(
        BlockDefinition airDefinition,
        BlockDefinition waterDefinition,
        BlockDefinition lavaDefinition,
        BlockDefinition powderSnowDefinition,
        IntList airIds,
        IntUnaryOperator toIntermediary,
        IntUnaryOperator fromIntermediary
) {

    public int airId() {
        return this.airDefinition.getRuntimeId();
    }

    public int waterId() {
        return this.waterDefinition.getRuntimeId();
    }
}
