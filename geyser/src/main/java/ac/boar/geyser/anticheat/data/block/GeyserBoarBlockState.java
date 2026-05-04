package ac.boar.geyser.anticheat.data.block;

import ac.boar.anticheat.collision.BedrockCollision;
import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.Reference;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.geyser.anticheat.util.block.GeyserBlockUtil;
import ac.boar.geyser.mappings.block.GeyserBlock;
import ac.boar.geyser.mappings.block.GeyserProperty;
import ac.boar.geyser.model.GeyserNetworkSession;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.BlockMappings;
import ac.boar.mappings.block.Property;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.block.type.SkullBlock;
import org.geysermc.geyser.level.physics.Axis;
import org.geysermc.geyser.level.physics.BoundingBox;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.SkullCache;
import org.geysermc.geyser.translator.collision.BlockCollision;
import org.geysermc.geyser.translator.collision.SolidCollision;
import org.geysermc.geyser.util.BlockUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RequiredArgsConstructor
@Getter
public class GeyserBoarBlockState implements BoarBlockState {
    private final BlockState state;
    private final Vector3i position;
    private final int layer;

    @Override
    public int intermediaryId() {
        return this.state.javaId();
    }

    @Override
    public Block block() {
        return new GeyserBlock(this.state.block());
    }

    @Override
    public BlockDefinition definition(BoarPlayer player) {
        GeyserSession session = ((GeyserNetworkSession) player.getSession()).session();
        if (this.state.block() instanceof SkullBlock skullBlock && skullBlock.skullType() == SkullBlock.Type.PLAYER) {
            SkullCache.Skull skull = session.getSkullCache().getSkulls().get(this.position);
            if (skull != null && skull.getBlockDefinition() != null) {
                return skull.getBlockDefinition();
            }
        }

        return session.getBlockMappings().getBedrockBlock(this.state);
    }

    @Override
    public <T extends Comparable<T>> BoarBlockState with(Property<T> property, T value) {
        return new GeyserBoarBlockState(this.state.withValue(((GeyserProperty<T>) property).handle(), value), this.position, this.layer);
    }

    @Override
    public boolean isWaterlogged() {
        return BlockRegistries.WATERLOGGED.get().get(this.state.javaId());
    }

    @Override
    public boolean isFaceSturdy(BoarPlayer player) {
        if (this.state.is(Blocks.SCAFFOLDING)) {
            return false;
        }

        return BlockUtils.getCollision(state.javaId()) instanceof SolidCollision;
    }

    @Override
    public boolean isAir() {
        return state.is(Blocks.AIR) || state.is(Blocks.CAVE_AIR) || state.is(Blocks.VOID_AIR);
    }

    @Override
    public void onSteppedOn(final BoarPlayer player, final Vector3i vector3i) {
    }

    @Override
    public boolean blocksMotion(final BoarPlayer player) {
        return !state.is(Blocks.COBWEB) && !state.is(Blocks.BAMBOO_SAPLING) && this.isSolid(player);
    }

    @Override
    public void entityInside(final BoarPlayer player, Mutable pos) {
        if (this.state.is(Blocks.POWDER_SNOW) && player.boundingBox.offset(0, 1.0E-3F, 0).contains(pos.getX(), pos.getY(), pos.getZ())) { // UHHHHHHHHHHHHH
            return;
        }

        if (this.state.is(Blocks.BUBBLE_COLUMN)) {
            boolean drag = this.state.getValue(Properties.DRAG);

            final Vec3 lv = player.velocity;
            if (player.compensatedWorld.getBlockState(pos.getX(), pos.getY() + 1, pos.getZ(), 0).isAir()) {
                if (drag) {
                    lv.y = Math.max(-0.9F, lv.y - 0.03F);
                } else {
                    lv.y = Math.min(1.8F, lv.y + 0.1F);
                }
            } else {
                if (drag) {
                    lv.y = Math.max(-0.3F, lv.y - 0.03F);
                } else {
                    lv.y = Math.min(0.7F, lv.y + 0.06F);
                }
            }
        }

        Vec3 movementMultiplier = Vec3.ZERO;
        if (state.is(Blocks.SWEET_BERRY_BUSH)) {
            movementMultiplier = new Vec3(0.8F, 0.75F, 0.8F);
        } else if (state.is(Blocks.POWDER_SNOW) && player.position.y < pos.getY() + 1 - 1.0E-5f) {
            movementMultiplier = new Vec3(0.9F, 1.5F, 0.9F);
        } else if (state.is(Blocks.COBWEB)) {
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
        BlockState state = this.state;

        if (BlockMappings.get().getFenceBlocks().contains(this.block())) {
            state = GeyserBlockUtil.findFenceBlockState(player, getState(), pos);
        } else if (state.is(Blocks.IRON_BARS) || state.toString().toLowerCase(Locale.ROOT).contains("glass_pane")) {
            state = GeyserBlockUtil.findIronBarsBlockState(player, getState(), pos);
        } else if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            state = GeyserBlockUtil.findChestState(player, state, pos);
        } else if (BlockMappings.get().getStairsBlocks().contains(this.block())) {
            state = state.withValue(Properties.STAIRS_SHAPE, GeyserBlockUtil.getStairShape(player, state, pos));
        }

        final List<Box> list = new ArrayList<>();
        final List<Box> collisions = BedrockCollision.getCollisionBox(player, playerAABB, pos, this);
        if (collisions != null) {
            for (Box aabb : collisions) {
                aabb = aabb.offset(pos.getX(), pos.getY(), pos.getZ());
                if (!checkAAB || aabb.intersects(playerAABB)) {
                    list.add(aabb);
                }
            }
            return list;
        }

        final BlockCollision collision = BlockUtils.getCollision(state.javaId());
        if (collision == null) {
            return list;
        }

        for (final BoundingBox geyserBB : collision.getBoundingBoxes()) {
            final Box box = createBox(geyserBB).offset(pos.getX(), pos.getY(), pos.getZ());

            if (!checkAAB || box.intersects(playerAABB)) {
                list.add(box);
            }
        }

        return list;
    }

    @Override
    public List<Box> getCollisionBoxes() {
        List<Box> collisions = new ArrayList<>();
        for (BoundingBox boundingBox : BlockUtils.getCollision(state.javaId()).getBoundingBoxes()) {
            collisions.add(new Box(
                    (float) boundingBox.getMin(Axis.X),
                    (float) boundingBox.getMin(Axis.Y),
                    (float) boundingBox.getMin(Axis.Z),
                    (float) boundingBox.getMax(Axis.X),
                    (float) boundingBox.getMax(Axis.Y),
                    (float) boundingBox.getMax(Axis.Z)
            ));
        }

        return collisions;
    }

    @Override
    public float getJumpFactor() {
        return state.is(Blocks.HONEY_BLOCK) ? 0.6F : 1;
    }

    @Override
    public float getFriction() {
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.FROSTED_ICE)) {
            return 0.98F;
        } else if (state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK)) {
            return 0.8F;
        } else if (state.is(Blocks.BLUE_ICE)) {
            return 0.989F;
        }

        return 0.6F;
    }

    @Override
    public FluidState getFluidState(int level) {
        if (level == 1) {
            if (this.state.is(Blocks.WATER)) {
                return new FluidState(Fluid.WATER, 8 / 9F, 8); // Waterlogged
            }

            return new FluidState(Fluid.EMPTY, 0, 0);
        }

        boolean water = this.state.is(Blocks.WATER);
        if (!water && !this.state.is(Blocks.LAVA)) {
            return new FluidState(Fluid.EMPTY, 0, 0);
        }

        Fluid fluid = water ? Fluid.WATER : Fluid.LAVA;

        int rawLevel = this.state.getValue(Properties.LEVEL);
        if (rawLevel == 0 || rawLevel == 8) {
            return new FluidState(fluid, 8 / 9F, rawLevel);
        }

        return new FluidState(fluid, (8 - rawLevel) / 9F, rawLevel);
    }

    @Override
    public boolean is(Block block) {
        return this.state.is(((GeyserBlock) block).handle());
    }

    @Override
    public boolean is(Reference<Block> block) {
        Optional<Block> opt = block.find();
        return opt.isPresent() ? is(opt.get()) : false;
    }

    @Override
    public <T extends Comparable<T>> T get(Property<T> property) {
        return this.state.getValue(((GeyserProperty<T>) property).handle());
    }

    private static Box createBox(BoundingBox boundingBox) {
        return new Box(
                (float) boundingBox.getMin(Axis.X),
                (float) boundingBox.getMin(Axis.Y),
                (float) boundingBox.getMin(Axis.Z),
                (float) boundingBox.getMax(Axis.X),
                (float) boundingBox.getMax(Axis.Y),
                (float) boundingBox.getMax(Axis.Z)
        );
    }
}
