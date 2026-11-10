package ac.boar.anticheat.prediction.vehicle;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Box;
import ac.boar.mappings.block.HorseJumpMaterial;

import java.util.OptionalInt;

public final class HorseJumpBlocks {
    private HorseJumpBlocks() {}

    // Launch block factor from preventsJump: 0.6 on a block that prevents jumps (honey), else 1.0. NaN when a block is unknown (null).
    public static float factor(final Boolean prevented) {
        if (prevented == null) {
            return Float.NaN;
        }
        return prevented ? 0.6F : 1.0F;
    }

    // Does the block under the horse prevent a jump (honey)? null when a block is unknown.
    // Checks the feet block first. Checks the block below when the feet block is a carpet or the box bottom is on a block edge.
    // The client uses the same probe for step height (VariableMaxAutoStepSystemImpl::tickEntity).
    public static Boolean preventsJump(final BoarPlayer player, final Box box) {
        final int x = (int) Math.floor(player.position.x), y = (int) Math.floor(box.minY), z = (int) Math.floor(player.position.z);
        final HorseJumpMaterial feet = material(player, x, y, z);
        if (!feet.known) {
            return null;
        }
        if (feet.preventsJump) {
            return true;
        }
        if (feet.belowProbe || box.minY <= (float) y) {
            final HorseJumpMaterial below = material(player, x, y - 1, z);
            if (!below.known) {
                return null;
            }
            return below.preventsJump;
        }
        return false;
    }

    private static HorseJumpMaterial material(final BoarPlayer player, final int x, final int y, final int z) {
        final OptionalInt raw = player.compensatedWorld.getKnownMappedRawBlockAt(x, y, z, 0);
        if (raw.isEmpty()) {
            return HorseJumpMaterial.UNKNOWN;
        }
        // Never null. The only adapter (GeyserHorseJumpMaterials.create) maps every ID to a material.
        // Also, BlockMappingInfo's horse jump constructor marks an ID with a null material as not defined, so isKnownRawId in getKnownMappedRawBlockAt already failed.
        return player.mappingInfo.horseJumpMaterial().apply(raw.getAsInt());
    }
}
