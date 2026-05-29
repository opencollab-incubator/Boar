package ac.boar.anticheat.util.block;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.mappings.block.Block;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;

public final class BlockUtil {

    public static void restoreCorrectBlock(BoarPlayer player, Vector3i vector, BoarBlockState blockState) {
        BlockDefinition bedrockBlock = blockState.definition(player);

        UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
        updateBlockPacket.setDataLayer(0);
        updateBlockPacket.setBlockPosition(vector);
        updateBlockPacket.setDefinition(bedrockBlock);
        updateBlockPacket.getFlags().addAll(UpdateBlockPacket.FLAG_ALL_PRIORITY);
        player.getConnection().sendPacket(updateBlockPacket);

        UpdateBlockPacket updateWaterPacket = new UpdateBlockPacket();
        updateWaterPacket.setDataLayer(1);
        updateWaterPacket.setBlockPosition(vector);
        updateWaterPacket.setDefinition(blockState.isWaterlogged() ? player.mappingInfo.waterDefinition() : player.mappingInfo.airDefinition());
        updateWaterPacket.getFlags().addAll(UpdateBlockPacket.FLAG_ALL_PRIORITY);
        player.getConnection().sendPacket(updateWaterPacket);

        // Reset the item in hand to prevent "missing" blocks
        player.getInventoryAccessor().updateSlot(player.getInventoryAccessor().heldItemSlot()); // TODO test
    }

    public static Vector3i getBlockPosition(Vector3i blockPos, int face) {
        return switch (face) {
            case 0 -> blockPos.sub(0, 1, 0);
            case 1 -> blockPos.add(0, 1, 0);
            case 2 -> blockPos.sub(0, 0, 1);
            case 3 -> blockPos.add(0, 0, 1);
            case 4 -> blockPos.sub(1, 0, 0);
            case 5 -> blockPos.add(1, 0, 0);
            default -> blockPos;
        };
    }

    public static void restoreCorrectBlock(BoarPlayer player, Vector3i blockPos) {
        restoreCorrectBlock(player, blockPos, player.getWorldAccessor().blockStateAt(blockPos, 0));
    }

    public static BoarBlockState getPlacementState(BoarPlayer player, Block block, Vector3i position) {
        return block.defaultBlockState(position, 0);
    }

    public static boolean determineCanBreak(final BoarPlayer player, final BoarBlockState state) {
        if (player.mappingInfo.airIds().contains(state.definition(player).getRuntimeId())) {
            return false;
        }

        float destroyTime = state.block().destroyTime();
        return destroyTime != -1 || player.gameType == GameType.CREATIVE;
    }
}
