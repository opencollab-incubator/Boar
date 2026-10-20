package ac.boar.anticheat.util.block;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Axis;
import ac.boar.anticheat.util.math.Direction;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.Blocks;
import ac.boar.mappings.block.Properties;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;

public final class BlockUtil {

    public static Vector3i getDoorBottom(BoarPlayer player, Vector3i pos, BoarBlockState state) {
        int below = 0;
        while (below < 16 && player.compensatedWorld.getBlockState(pos.getX(), pos.getY() - below - 1, pos.getZ(), 0).is(state.block())) {
            below++;
        }
        return below % 2 == 1 ? pos.sub(0, 1, 0) : pos;
    }

    public static void restoreCorrectBlock(BoarPlayer player, Vector3i vector, BoarBlockState blockState) {
        if (player.disableMitigations()) {
            return;
        }

        syncBlock(player, vector, blockState);
        // Reset the item in hand to prevent "missing" blocks, needs more testing
        player.getInventoryAccessor().updateSlot(player.getInventoryAccessor().heldItemSlot());
    }

    public static void syncBlock(BoarPlayer player, Vector3i vector) {
        syncBlock(player, vector, player.getWorldAccessor().blockStateAt(vector, 0));
    }

    public static void syncBlock(BoarPlayer player, Vector3i vector, BoarBlockState blockState) {
        // Skip the block resync if:
        // - a server update for this spot is still on its way to the client
        // - the client just placed a block here and the server hasn't answered yet
        // Sending ours could overwrite either with an older state (e.g. air under a bridging player)
        if (!player.blockPlacements.hasPendingServerUpdate(vector) && !player.blockPlacements.isUnansweredPlacement(vector)) {
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
        }
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

    // The state a block gets when the player places it. face is the side of the clicked block (Direction ordinal).
    // Only ladders are handled: they face away from the wall they hang on, which is the clicked side.
    // Other blocks (stairs, slabs, logs...) still use the default state.
    public static BoarBlockState getPlacementState(BoarPlayer player, Block block, Vector3i position, int face) {
        BoarBlockState state = block.defaultBlockState(position, 0);
        if (block.is(Blocks.LADDER) && face >= 0 && face < Direction.VALUES.length && Direction.VALUES[face].getAxis() != Axis.Y) {
            state = state.with(Properties.HORIZONTAL_FACING, Direction.VALUES[face]);
        }

        return state;
    }

    public static boolean determineCanBreak(final BoarPlayer player, final BoarBlockState state) {
        if (player.mappingInfo.airIds().contains(state.definition(player).getRuntimeId())) {
            return false;
        }

        float destroyTime = state.block().destroyTime();
        return destroyTime != -1 || player.gameType == GameType.CREATIVE;
    }
}
