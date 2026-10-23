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

    // The state a block gets when the player places it. face is the side of the clicked block (Direction ordinal), clickY is the click height inside it.
    // Only ladders and slabs are handled. Other blocks (stairs, logs...) still use the default state.
    public static BoarBlockState getPlacementState(BoarPlayer player, Block block, Vector3i position, int face, float clickY) {
        BoarBlockState state = block.defaultBlockState(position, 0);
        // Ladders face away from the wall they hang on, which is the clicked side.
        if (block.is(Blocks.LADDER) && face >= 0 && face < Direction.VALUES.length && Direction.VALUES[face].getAxis() != Axis.Y) {
            state = state.with(Properties.HORIZONTAL_FACING, Direction.VALUES[face]);
        }

        // Slabs go on top against a bottom face, at the bottom against a top face, and otherwise follow the click height (UpdateVerticalHalfGetPlacementBlockCallback::getPlacementBlock).
        if (getSlabType(state) != null) {
            boolean top = face == Direction.DOWN.ordinal() || face != Direction.UP.ordinal() && clickY > 0.5F;
            state = state.with(Properties.SLAB_TYPE, top ? "top" : "bottom");
        }

        return state;
    }

    // Where a slab item turns a single slab of the same block into a double slab, or null if it does not (SlabBlockItem::_useOn)
    public static Vector3i getSlabMergePosition(BoarPlayer player, Block slab, Vector3i clicked, int face) {
        BoarBlockState clickedState = player.compensatedWorld.getBlockState(clicked, 0);
        String clickedType = clickedState.is(slab) ? getSlabType(clickedState) : null;
        if ("bottom".equals(clickedType) && face == Direction.UP.ordinal() || "top".equals(clickedType) && face == Direction.DOWN.ordinal()) {
            return clicked;
        }

        Vector3i next = getBlockPosition(clicked, face);
        BoarBlockState nextState = player.compensatedWorld.getBlockState(next, 0);
        String nextType = nextState.is(slab) ? getSlabType(nextState) : null;
        if ("bottom".equals(nextType) || "top".equals(nextType)) {
            return next;
        }

        return null;
    }

    // "top", "bottom" or "double" for a slab, or null for any other block. Some platforms throw for a block without this property.
    public static String getSlabType(BoarBlockState state) {
        try {
            return state.get(Properties.SLAB_TYPE);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean determineCanBreak(final BoarPlayer player, final BoarBlockState state) {
        if (player.mappingInfo.airIds().contains(state.definition(player).getRuntimeId())) {
            return false;
        }

        float destroyTime = state.block().destroyTime();
        return destroyTime != -1 || player.gameType == GameType.CREATIVE;
    }
}
