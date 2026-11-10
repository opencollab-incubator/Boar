package ac.boar.anticheat.collision;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Box;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.BlockMappings;
import org.cloudburstmc.math.vector.Vector3i;

/**
 * Collects one-way collision shapes (doors, trapdoors, fence gates) from the blocks an actor's box is inside, after its move.
 * Called for the player in the block-inside pass (EntityTicker.applyEffectsFromBlocks) and for the boat box (BoatTicker.simulate).
 */
public final class OneWayCollision {
    private OneWayCollision() {}

    private static void collectInsideBlock(final BoarPlayer player, final DepenetrationState depenetration, final Box actorBox, final BoarBlockState state,
                                           final Vector3i position) {
        final Block block = state.block();
        final BlockMappings mappings = BlockMappings.get();
        if (!mappings.getDoorBlocks().contains(block) && !mappings.getTrapDoorBlocks().contains(block) && !mappings.getFenceGateBlocks().contains(block)) {
            return;
        }

        // The inside scan already decided this block counts. Store its collision shape, including open doors.
        // An open gate has no shape with size, so it adds nothing.
        for (final Box shape : state.findCollision(player, position, actorBox, false)) {
            if (shape.maxX > shape.minX && shape.maxY > shape.minY && shape.maxZ > shape.minZ) {
                depenetration.addOneWayBox(shape);
            }
        }
    }

    public static void collectInsideActor(final BoarPlayer player, final DepenetrationState depenetration, final Box actorBox) {
        // These sums must round as floats before flooring, also at large world coordinates.
        final Vector3i min = Vector3i.from(actorBox.minX + 0.001F, actorBox.minY + 0.001F, actorBox.minZ + 0.001F);
        final Vector3i max = Vector3i.from(actorBox.maxX - 0.001F, actorBox.maxY - 0.001F, actorBox.maxZ - 0.001F);
        if (!player.compensatedWorld.hasChunksAt(min.getX(), min.getZ(), max.getX(), max.getZ())) {
            return;
        }
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    collectInsideBlock(player, depenetration, actorBox, player.compensatedWorld.getBlockState(x, y, z, 0), Vector3i.from(x, y, z));
                }
            }
        }
    }
}
