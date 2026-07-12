package ac.boar.anticheat.data.block;

import ac.boar.anticheat.collision.BedrockCollision;
import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.Reference;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.Blocks;
import ac.boar.mappings.block.Properties;
import ac.boar.mappings.block.Property;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractBoarBlockState implements BoarBlockState {
    protected final BoarBlockStateDelegate delegate;

    protected AbstractBoarBlockState(BoarBlockStateDelegate delegate) {
        this.delegate = delegate;
    }

    public BoarBlockStateDelegate delegate() {
        return delegate;
    }

    @Override
    public int intermediaryId() {
        return delegate.intermediaryId();
    }

    @Override
    public Block block() {
        return delegate.block();
    }

    @Override
    public BlockDefinition definition(BoarPlayer player) {
        return delegate.definition(player);
    }

    @Override
    public <T extends Comparable<T>> BoarBlockState with(Property<T> property, T value) {
        return delegate.with(property, value);
    }

    @Override
    public boolean isWaterlogged() {
        return delegate.isWaterlogged();
    }

    @Override
    public boolean is(Block block) {
        return delegate.is(block);
    }

    @Override
    public boolean is(Reference<Block> reference) {
        return reference.find().map(this::is).orElse(false);
    }

    @Override
    public <T extends Comparable<T>> T get(Property<T> property) {
        return delegate.get(property);
    }

    @Override
    public List<Box> getCollisionBoxes() {
        return delegate.getCollisionBoxes();
    }

    @Override
    public boolean isFaceSturdy(BoarPlayer player) {
        if (is(Blocks.SCAFFOLDING)) {
            return false;
        }
        return delegate.isFaceSturdy(player);
    }

    @Override
    public boolean isAir() {
        return is(Blocks.AIR) || is(Blocks.CAVE_AIR) || is(Blocks.VOID_AIR);
    }

    @Override
    public Vector3i getPosition() {
        return delegate.getPosition();
    }

    @Override
    public int getLayer() {
        return delegate.getLayer();
    }

    @Override
    public boolean blocksMotion(BoarPlayer player) {
        return !is(Blocks.COBWEB) && !is(Blocks.BAMBOO_SAPLING) && isSolid(player);
    }

    @Override
    public void onSteppedOn(BoarPlayer player, Vector3i vector3i) {
    }

    @Override
    public void entityInside(BoarPlayer player, Mutable pos) {
        if (is(Blocks.POWDER_SNOW) && player.boundingBox.offset(0, 1.0E-3F, 0).contains(pos.getX(), pos.getY(), pos.getZ())) {
            return;
        }

        if (is(Blocks.BUBBLE_COLUMN)) {
            boolean drag = get(Properties.DRAG);

            Vec3 velocity = player.velocity;
            if (player.compensatedWorld.getBlockState(pos.getX(), pos.getY() + 1, pos.getZ(), 0).isAir()) {
                if (drag) {
                    velocity.y = Math.max(-0.9F, velocity.y - 0.03F);
                } else {
                    velocity.y = Math.min(1.8F, velocity.y + 0.1F);
                }
            } else {
                if (drag) {
                    velocity.y = Math.max(-0.3F, velocity.y - 0.03F);
                } else {
                    velocity.y = Math.min(0.7F, velocity.y + 0.06F);
                }
            }
        }

        Vec3 movementMultiplier = Vec3.ZERO;
        if (is(Blocks.SWEET_BERRY_BUSH)) {
            movementMultiplier = new Vec3(0.8F, 0.75F, 0.8F);
        } else if (is(Blocks.POWDER_SNOW) && player.position.y < pos.getY() + 1 - 1.0E-5f) {
            movementMultiplier = new Vec3(0.9F, 1.5F, 0.9F);
        } else if (is(Blocks.COBWEB)) {
            movementMultiplier = new Vec3(0.25F, 0.05F, 0.25F);
            if (player.hasEffect(Effect.WEAVING)) {
                movementMultiplier = new Vec3(0.5F, 0.25F, 0.5F);
            }
        }

        if (movementMultiplier.equals(Vec3.ZERO)) {
            return;
        }

        final boolean xLargerThanThreshold = Math.abs(player.stuckSpeedMultiplier.x) >= 1.0E-7;
        final boolean yLargerThanThreshold = Math.abs(player.stuckSpeedMultiplier.y) >= 1.0E-7;
        final boolean zLargerThanThreshold = Math.abs(player.stuckSpeedMultiplier.z) >= 1.0E-7;
        if (xLargerThanThreshold || yLargerThanThreshold || zLargerThanThreshold) {
            player.stuckSpeedMultiplier.x = Math.min(player.stuckSpeedMultiplier.x, movementMultiplier.x);
            player.stuckSpeedMultiplier.y = Math.min(player.stuckSpeedMultiplier.y, movementMultiplier.y);
            player.stuckSpeedMultiplier.z = Math.min(player.stuckSpeedMultiplier.z, movementMultiplier.z);
        } else {
            player.stuckSpeedMultiplier = movementMultiplier;
        }
    }

    @Override
    public void updateEntityMovementAfterFallOn(BoarPlayer player, boolean living) {
        player.velocity.y = 0;
    }

    @Override
    public List<Box> findCollision(BoarPlayer player, Vector3i pos, Box playerAABB, boolean checkAAB) {
        List<Box> list = new ArrayList<>();
        List<Box> bedrockCollisions = BedrockCollision.getCollisionBox(player, playerAABB, pos, this);
        if (bedrockCollisions != null) {
            for (Box box : bedrockCollisions) {
                Box offset = box.offset(pos.getX(), pos.getY(), pos.getZ());
                if (!checkAAB || offset.intersects(playerAABB)) {
                    list.add(offset);
                }
            }
            return list;
        }

        BoarBlockState reshaped = delegate.applyConnectionShape(player, this, pos);
        for (Box box : reshaped.getCollisionBoxes()) {
            Box offset = box.offset(pos.getX(), pos.getY(), pos.getZ());
            if (!checkAAB || offset.intersects(playerAABB)) {
                list.add(offset);
            }
        }
        return list;
    }

    @Override
    public float getJumpFactor() {
        return is(Blocks.HONEY_BLOCK) ? 0.6F : 1;
    }

    @Override
    public float getBlockBounciness() {
        return 0;
    }

    @Override
    public float getFriction() {
        if (is(Blocks.ICE) || is(Blocks.PACKED_ICE) || is(Blocks.FROSTED_ICE)) {
            return 0.98F;
        } else if (is(Blocks.SLIME_BLOCK) || is(Blocks.HONEY_BLOCK)) {
            return 0.8F;
        } else if (is(Blocks.BLUE_ICE)) {
            return 0.989F;
        }
        return 0.6F;
    }

    @Override
    public FluidState getFluidState(int level) {
        if (level == 1) {
            if (is(Blocks.WATER)) {
                return new FluidState(Fluid.WATER, 8 / 9F, 8);
            }
            return new FluidState(Fluid.EMPTY, 0, 0);
        }

        boolean water = is(Blocks.WATER);
        if (!water && !is(Blocks.LAVA)) {
            return new FluidState(Fluid.EMPTY, 0, 0);
        }

        Fluid fluid = water ? Fluid.WATER : Fluid.LAVA;
        int rawLevel = get(Properties.LEVEL);
        if (rawLevel == 0 || rawLevel == 8) {
            return new FluidState(fluid, 8 / 9F, rawLevel);
        }
        return new FluidState(fluid, (8 - rawLevel) / 9F, rawLevel);
    }
}
