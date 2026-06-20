package ac.boar.anticheat.data.block;

import ac.boar.anticheat.collision.BedrockCollision;
import ac.boar.anticheat.data.block.impl.BedBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.block.BlockUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.BlockMappings;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.BoundingBox;
import org.geysermc.geyser.translator.collision.BlockCollision;
import org.geysermc.geyser.translator.collision.SolidCollision;
import org.geysermc.geyser.util.BlockUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@Getter
public class BoarBlockState {
    private final BlockState state;
    private final Vector3i position;
    private final int layer;

    public boolean isFaceSturdy(BoarPlayer player) {
        if (this.state.is(Blocks.SCAFFOLDING)) {
            return false;
        }

        return BlockUtils.getCollision(state.javaId()) instanceof SolidCollision;
    }

    public boolean isAir() {
        return state.is(Blocks.AIR) || state.is(Blocks.CAVE_AIR) || state.is(Blocks.VOID_AIR);
    }

    public void onSteppedOn(final BoarPlayer player, final Vector3i vector3i) {
    }

    public boolean blocksMotion(final BoarPlayer player) {
        return !state.is(Blocks.COBWEB) && !state.is(Blocks.BAMBOO_SAPLING) && this.isSolid(player);
    }

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

    private boolean isSolid(BoarPlayer player) {
        List<Box> boxes = findCollision(player, Vector3i.ZERO, Box.EMPTY, false);
        if (boxes.isEmpty()) {
            return false;
        } else {
            Box box = new Box(0, 0, 0, 0, 0, 0);
            for (Box box1 : boxes) {
                box = box1.union(box);
            }

            return box.getAverageSideLength() >= 0.7291666666666666 || box.getLengthY() >= 1.0;
        }
    }

    public void updateEntityMovementAfterFallOn(BoarPlayer player, boolean living) {
        player.velocity.y = 0;
    }

    public List<Box> findCollision(BoarPlayer player, Vector3i pos, Box playerAABB, boolean checkAAB) {
        BlockState state = this.state;

        if (BlockMappings.getFenceBlocks().contains(state.block())) {
            state = BlockUtil.findFenceBlockState(player, getState(), pos);
        } else if (state.is(Blocks.IRON_BARS) || state.toString().toLowerCase(Locale.ROOT).contains("glass_pane")) {
            state = BlockUtil.findIronBarsBlockState(player, getState(), pos);
        } else if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            state = BlockUtil.findChestState(player, state, pos);
        } else if (BlockMappings.getStairsBlocks().contains(state.block())) {
            state = state.withValue(Properties.STAIRS_SHAPE, BlockUtil.getStairShape(player, state, pos));
        }

        final List<Box> list = new ArrayList<>();
        final List<Box> collisions = BedrockCollision.getCollisionBox(player, playerAABB, pos, state);
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
            final Box box = new Box(geyserBB).offset(pos.getX(), pos.getY(), pos.getZ());

            if (!checkAAB || box.intersects(playerAABB)) {
                list.add(box);
            }
        }

        return list;
    }

    public float getJumpFactor() {
        return state.is(Blocks.HONEY_BLOCK) ? 0.6F : 1;
    }

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

    public float getBlockBounciness() {
        if (state.is(Blocks.SLIME_BLOCK)) {
            return 1;
        } else if (this instanceof BedBlockState) {
            return 0.75f;
        }

        return 0;
    }
}