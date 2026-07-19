package ac.boar.anticheat.data;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Direction;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.block.Blocks;
import org.cloudburstmc.math.vector.Vector3i;

public record FluidState(Fluid fluid, float height, int level) {
    public float getHeight(final BoarPlayer player, final Mutable pos) {
        return isFluidAboveEqual(player, pos) ? 1.0F : this.height();
    }

    private boolean isFluidAboveEqual(BoarPlayer player, Mutable pos) {
        return fluid == player.compensatedWorld.getFluidState(pos.getX(), pos.getY() + 1, pos.getZ()).fluid();
    }

    public Vec3 getFlow(final BoarPlayer player, final Vector3i vector3i) {
        if (player.compensatedWorld.getBlockState(vector3i, 0).is(Blocks.BUBBLE_COLUMN)) {
            return new Vec3(0, 0, 0);
        }

        Vec3 vec3 = new Vec3(0, 0, 0);
        int i = this.getEffectiveFlowDecay();

        final Mutable mutable = new Mutable();
        for (Direction direction : Direction.HORIZONTAL) {
            mutable.set(vector3i, direction.getUnitVector());
            final FluidState fluidState1 = player.compensatedWorld.getFluidState(mutable);
            int j = fluidState1.fluid() == this.fluid() ? fluidState1.getEffectiveFlowDecay() : -1;

            if (j < 0) {
                if (!player.compensatedWorld.getBlockState(mutable, 0).blocksMotion(player)) {
                    FluidState below = player.compensatedWorld.getFluidState(Vector3i.from(mutable.getX(), mutable.getY() - 1, mutable.getZ()));
                    if (below.fluid() == this.fluid()) {
                        j = below.getEffectiveFlowDecay();
                        if (j >= 0) {
                            int k = j - (i - 8);
                            vec3 = vec3.add((mutable.getX() - vector3i.getX()) * k, (mutable.getY() - vector3i.getY()) * k, (mutable.getZ() - vector3i.getZ()) * k);
                        }
                    }
                }
            } else {
                int l = j - i;
                vec3 = vec3.add((mutable.getX() - vector3i.getX()) * l, (mutable.getY() - vector3i.getY()) * l, (mutable.getZ() - vector3i.getZ()) * l);
            }
        }

        if (this.level() >= 8) {
            for (Direction direction : Direction.HORIZONTAL) {
                Vector3i blockpos1 = vector3i.add(direction.getUnitVector());

                if (this.isSolidFace(player, blockpos1, direction) || this.isSolidFace(player, blockpos1.up(), direction)) {
                    vec3 = vec3.normalize().add(0, -6, 0);
                    break;
                }
            }
        }

        return vec3.normalize();
    }

    public int getEffectiveFlowDecay() {
        return this.level() >= 8 ? 0 : this.level();
    }

    private boolean isSolidFace(BoarPlayer player, Vector3i blockPos, Direction direction) {
        BoarBlockState blockState = player.compensatedWorld.getBlockState(blockPos, 0);
        FluidState fluidState = player.compensatedWorld.getFluidState(blockPos);
        if (fluidState.fluid() == fluid()) {
            return false;
        }
        if (direction == Direction.UP) {
            return true;
        }

        if (blockState.is(Blocks.ICE) || blockState.is(Blocks.FROSTED_ICE) || blockState.is(Blocks.BLUE_ICE) || blockState.is(Blocks.PACKED_ICE)) {
            return false;
        }
        return blockState.isFaceSturdy(player);
    }
}
