package ac.boar.anticheat.data.block;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Axis;
import ac.boar.anticheat.util.math.Direction;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.mappings.block.BlockMappings;
import ac.boar.mappings.block.Properties;
import org.cloudburstmc.math.vector.Vector3i;

// StairBlock::liquidCanFlowIntoFromDirection
public final class StairLiquidFlow {
    private StairLiquidFlow() {
    }

    // LiquidBlockBase::_getFlow.
    public static boolean canFlowBetween(final BoarPlayer player, final Vector3i current, final Mutable neighbor, final Direction direction) {
        final Direction incoming = Direction.VALUES[direction.ordinal() ^ 1];
        return isHorizontalFaceOpen(player, neighbor.getX(), neighbor.getY(), neighbor.getZ(), incoming) && isHorizontalFaceOpen(player, current.getX(), current.getY(), current.getZ(), direction);
    }

    private static boolean isHorizontalFaceOpen(final BoarPlayer player, final int x, final int y, final int z, final Direction face) {
        final BoarBlockState block = player.compensatedWorld.getBlockState(x, y, z, 0);
        if (!BlockMappings.get().getStairsBlocks().contains(block.block())) {
            return true;
        }

        final Direction facing = block.get(Properties.HORIZONTAL_FACING);
        if (facing == null || facing.getAxis() == Axis.Y || face.getAxis() == Axis.Y || face == facing) {
            return false;
        }
        if (face.getAxis() == facing.getAxis()) {
            return true;
        }

        final Vector3i offset = facing.getUnitVector();
        final BoarBlockState barrier = player.compensatedWorld.getBlockState(x - offset.getX(), y, z - offset.getZ(), 0);
        return !barrier.is(block.block()) || barrier.get(Properties.HORIZONTAL_FACING) != face; // ref StairBlock::_neighboringBlockCheckForCreatingBarrierInDirection and StairBlock::StairBlock.
    }
}
