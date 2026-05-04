package ac.boar.anticheat.compensated.world;

import ac.boar.anticheat.collision.util.CuboidBlockIterator;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.compensated.world.base.CompensatedWorld;
import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.mappings.block.Blocks;
import com.google.common.collect.ImmutableList;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.ArrayList;
import java.util.List;

public class CompensatedWorldImpl extends CompensatedWorld {
    public CompensatedWorldImpl(BoarPlayer player) {
        super(player);
    }

    public boolean noCollision(Box aabb) {
        return this.collectColliders(this.getEntityCollisions(aabb), aabb).isEmpty();
    }

    public FluidState getFluidState(final Vector3i vec3) {
        return this.getFluidState(vec3.getX(), vec3.getY(), vec3.getZ());
    }

    public FluidState getFluidState(final Mutable mutable) {
        return this.getFluidState(mutable.getX(), mutable.getY(), mutable.getZ());
    }

    public FluidState getFluidState(int x, int y, int z) {
        BoarBlockState level1Block = getBlockState(x, y, z, 1);
        FluidState level1Fluid = level1Block.getFluidState(1);
        if (level1Fluid.fluid() == Fluid.WATER) {
            return level1Fluid;
        }

        return getBlockState(x, y, z, 0).getFluidState(0);
    }

    public List<Box> collectColliders(List<Box> list, Box aABB) {
        ImmutableList.Builder<Box> builder = ImmutableList.builderWithExpectedSize(list.size() + 1);
        if (!list.isEmpty()) {
            builder.addAll(list);
        }

        final CuboidBlockIterator iterator = CuboidBlockIterator.iterator(aABB);
        while (iterator.step()) {
            int x = iterator.getX(), y = iterator.getY(), z = iterator.getZ();
            if (this.isChunkLoaded(x, z)) {
                BoarBlockState state = this.getBlockState(x, y, z, 0);
                if (state.is(Blocks.BAMBOO) && new Box(x, y, z, x + 1, y + 1, z + 1).intersects(aABB)) {
                    getPlayer().nearBamboo = true;
                }

                builder.addAll(state.findCollision(this.getPlayer(), Vector3i.from(x, y, z), aABB, true));
            }
        }
        return builder.build();
    }

    public List<Box> getEntityCollisions(Box aABB) {
        final List<Box> boxes = new ArrayList<>();

        aABB = aABB.expand(1.0E-7F);

        // Sometimes this can spam error when player first join or something like that, can be safely ignore here.
        try {
            for (EntityCache cache : this.getEntities().values()) {
                if (cache == null || cache.getMetadata().getFlags() == null) {
                    continue;
                }
                Boolean collidable = cache.getMetadata().getFlags().get(EntityFlag.COLLIDABLE);
                if (collidable == null || !collidable) {
                    continue;
                }
                if (!aABB.intersects(cache.getCurrent().getBoundingBox())) {
                    continue;
                }

                // System.out.println("Collide able box: " + cache.getCurrent().getBoundingBox() + ", " + cache.getCurrent().getPos());
                boxes.add(cache.getCurrent().getBoundingBox());
            }
        } catch (Exception ignored) {}

        return boxes;
    }

    public boolean hasChunksAt(int i, int j, int k, int l) {
        for (int q = i; q <= k; ++q) {
            for (int r = j; r <= l; ++r) {
                if (this.isChunkLoaded(q, r)) continue;
                return false;
            }
        }
        return true;
    }
}
