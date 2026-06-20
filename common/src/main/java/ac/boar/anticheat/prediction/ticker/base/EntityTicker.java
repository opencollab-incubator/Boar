package ac.boar.anticheat.prediction.ticker.base;

import ac.boar.anticheat.collision.Collider;
import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.block.impl.BedBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.MovementDebug;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.block.Blocks;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

@RequiredArgsConstructor
public class EntityTicker {
    protected final BoarPlayer player;

    public void tick() {
        this.baseTick();
    }

    public void baseTick() {
        player.inBlockState = null;

        this.updateWaterState();
        // this.updateSubmergedInWaterState();
        this.updateSwimming();

        player.soulSandBelow = player.compensatedWorld.getBlockState(player.position.down(1.0E-3F).toVector3i(), 0).is(Blocks.SOUL_SAND);
    }

    private void updateSwimming() {
        if (player.getFlagTracker().has(EntityFlag.SWIMMING)) {
            player.getFlagTracker().set(EntityFlag.SWIMMING, player.touchingWater && player.vehicleData == null);
        }
    }

    private void updateWaterState() {
        player.fluidHeight.clear();
        this.checkWaterState();
        this.updateFluidHeightAndDoFluidPushing(0.007F, Fluid.LAVA);
    }

    void checkWaterState() {
//        if (this.getVehicle() instanceof AbstractBoatEntity lv && !lv.isSubmergedInWater()) {
//            this.touchingWater = false;
//            return;
//        }

        player.touchingWater = this.updateFluidHeightAndDoFluidPushing(0.014F, Fluid.WATER);
    }

    private boolean updateFluidHeightAndDoFluidPushing(final float speed, final Fluid tag) {
        if (player.isRegionUnloaded()) {
            return false;
        }

        Box box = player.boundingBox.expand(0, -0.4F, 0).contract(0.001F);
        if (tag == Fluid.LAVA) {
            box = box.contract(0.1F, 0, 0.1F);
        }

        int i = GenericMath.floor(box.minX);
        int j = GenericMath.floor(box.maxX + 1.0D);
        int k = GenericMath.floor(box.minY);
        int l = GenericMath.floor(box.maxY + 1.0D);
        int i1 = GenericMath.floor(box.minZ);
        int j1 = GenericMath.floor(box.maxZ + 1.0D);

        boolean found = false;
        Vec3 vec3 = new Vec3(0, 0, 0);
        float maxFluidHeight = Float.MIN_VALUE;
        Mutable mutable = new Mutable();

        for (int k1 = i; k1 < j; ++k1) { for (int l1 = k; l1 < l; ++l1) { for (int i2 = i1; i2 < j1; ++i2) {
            mutable.set(k1, l1, i2);
            FluidState fluidState = player.compensatedWorld.getFluidState(mutable);

            if (fluidState.fluid() == tag) {
                float d0 = l1 + 1 - fluidState.height();
                maxFluidHeight = Math.max(maxFluidHeight, d0);

                if (l >= d0) {
                    found = true;
                    vec3 = vec3.add(fluidState.getFlow(player, Vector3i.from(k1, l1, i2)));
                }
            }
        }}}

        if (!found) {
            maxFluidHeight = 0;
        }

        if (vec3.lengthSquared() > 0.0D) {
            vec3 = vec3.normalize().multiply(speed);
            player.velocity = player.velocity.add(vec3);
            MovementDebug.log(player, "FLUID-PUSH", tag + " push=" + MovementDebug.vec(vec3)
                    + " velAfterPush=" + MovementDebug.vec(player.velocity) + " maxFluidHeight=" + maxFluidHeight);
        }

        if (tag == Fluid.LAVA) {
            player.beingPushByLava = vec3.horizontalLengthSquared() > 1.0E-6;
        }

        player.fluidHeight.put(tag, maxFluidHeight);
        return found;
    }

    protected void applyEffectsFromBlocks() {
        if (player.onGround) {
            final Vector3i lv = player.getOnPos(0.2F);
            player.compensatedWorld.getBlockState(lv, 0).onSteppedOn(player, lv);
        }

        Vector3i min = Vector3i.from(player.boundingBox.minX + 0.001D, player.boundingBox.minY + 0.001D, player.boundingBox.minZ + 0.001D);
        Vector3i max = Vector3i.from(player.boundingBox.maxX - 0.001D, player.boundingBox.maxY - 0.001D, player.boundingBox.maxZ - 0.001D);

        final Mutable mutable = new Mutable();
        for (int i = min.getX(); i <= max.getX(); ++i) {for (int j = min.getY(); j <= max.getY(); ++j) {for (int k = min.getZ(); k <= max.getZ(); ++k) {
            mutable.set(i, j, k);

            player.compensatedWorld.getBlockState(i, j, k, 0).entityInside(player, mutable);
        }}}
    }

    public final void doSelfMove(Vec3 vec3) {
        if (player.abilities.contains(Ability.NO_CLIP)) {
            player.setPos(player.position.add(vec3));
            return;
        }

        if (player.stuckSpeedMultiplier.lengthSquared() > 1.0E-7) {
            MovementDebug.log(player, "COLLISION", "stuckSpeedMultiplier=" + MovementDebug.vec(player.stuckSpeedMultiplier)
                    + " applied, velocity zeroed, move scaled to=" + MovementDebug.vec(vec3.multiply(player.stuckSpeedMultiplier)));
            vec3 = vec3.multiply(player.stuckSpeedMultiplier);
            player.stuckSpeedMultiplier = Vec3.ZERO;
            player.velocity = Vec3.ZERO.clone();
        }

        Vec3 oldVec3 = vec3.clone();
        Vec3 vec32 = Collider.collide(player, vec3 = Collider.maybeBackOffFromEdge(player, vec3));
        player.setPos(player.position.add(vec32));

        if (MovementDebug.enabled() && (oldVec3.x != vec3.x || oldVec3.z != vec3.z)) {
            MovementDebug.log(player, "COLLISION", "edge back-off: requested=" + MovementDebug.vec(oldVec3)
                    + " afterBackOff=" + MovementDebug.vec(vec3));
        }

        boolean collidedX = !MathUtil.equal(vec3.x, vec32.x);
        boolean collidedZ = !MathUtil.equal(vec3.z, vec32.z);
        player.horizontalCollision = collidedX || collidedZ;
        player.verticalCollision = vec3.y != vec32.y;
        player.onGround = player.verticalCollision && vec3.y < 0.0;

        if (MovementDebug.enabled()) {
            MovementDebug.log(player, "COLLISION", "wanted=" + MovementDebug.vec(vec3)
                    + " collided=" + MovementDebug.vec(vec32)
                    + " collidedX=" + collidedX + " collidedZ=" + collidedZ
                    + " hCollision=" + player.horizontalCollision + " vCollision=" + player.verticalCollision
                    + " onGround=" + player.onGround
                    + " newPos=" + MovementDebug.vec(player.position));
        }

        // Hacks for when the player is taking zero velocity but still on ground next tick for whatever reason.
        // They will claim to be not colliding vertically this tick but still act like they're on ground next tick, nice.
        if (vec3.y == 0 && player.bestPossibility.getVelocity().y == 0 && player.bestPossibility.getType() == VectorType.VELOCITY && !MathUtil.equal(player.lastTickFinalVelocity.y, 0)) {
            player.verticalCollision = Collider.collide(player, player.lastTickFinalVelocity.clone()).y != player.lastTickFinalVelocity.y;
            player.onGround = player.verticalCollision && player.lastTickFinalVelocity.y < 0;
        }

        // The player is near bamboo, we don't know what the offsetting is so we let player decide this...
        if (player.nearBamboo && player.getInputData().contains(PlayerAuthInputData.HORIZONTAL_COLLISION)) {
            player.horizontalCollision = true;
            collidedX = player.unvalidatedTickEnd.x == 0;
            collidedZ = player.unvalidatedTickEnd.z == 0;
        }

        // Sneaking hacks, this is not entirely correct but works, not much room to abuse.
        if (oldVec3.x != vec3.x || oldVec3.z != vec3.z) {
            player.velocity = new Vec3(player.unvalidatedTickEnd.x == 0 ? 0 : player.velocity.x, player.velocity.y, player.unvalidatedTickEnd.z == 0 ? 0 : player.velocity.z);
        }

        // TODO: What the actual value actually? Player still able to bounce on slime despite being .375 block higher.
        Vector3i blockPos = player.getOnPos(0.378F);
        BoarBlockState blockState = player.compensatedWorld.getBlockState(blockPos, 0);

        if (player.getSession().is26_20OrHigher()) {
            if (Math.abs(player.velocity.y) > 0 && player.verticalCollision || player.horizontalCollision) {
                restituteMovementAfterCollisions(blockState, collidedX, collidedZ, vec32);
            }
        } else {
            if (player.horizontalCollision) {
                player.velocity = new Vec3(collidedX ? 0 : player.velocity.x, player.velocity.y, collidedZ ? 0 : player.velocity.z);
            }

            if (player.verticalCollision) {
                blockState.updateEntityMovementAfterFallOn(player, true);
            }
        }

        player.beforeCollision = vec3.clone();
        player.afterCollision = vec32.clone();

        if (MovementDebug.enabled()) {
            MovementDebug.log(player, "COLLISION", "post-adjust velocity=" + MovementDebug.vec(player.velocity)
                    + " nearBamboo=" + player.nearBamboo
                    + " beforeCollision=" + MovementDebug.vec(player.beforeCollision)
                    + " afterCollision=" + MovementDebug.vec(player.afterCollision));
        }
    }

    private void restituteMovementAfterCollisions(final BoarBlockState state, final boolean xCollision, final boolean zCollision, final Vec3 movement) {
        float restitution = 0 /* this.isSuppressingBounce() ? 0.0 : this.getEntityBounciness() */;
        Vec3 currentMovement = player.velocity;
        Vec3 movementAfterBounce = currentMovement.clone();
        if (xCollision) {
            movementAfterBounce.x = -currentMovement.x * restitution;
        }

        if (zCollision) {
            movementAfterBounce.z = currentMovement.z * restitution;
        }

        if (player.verticalCollision) {
            if (player.onGround) {
                restitution = !(-currentMovement.y < player.getEffectiveGravity()) && !player.getFlagTracker().has(EntityFlag.SNEAKING) && !state.is(Blocks.HONEY_BLOCK) ? Math.max(restitution, state.getBlockBounciness()) : 0.0f;
            }

            float gravityCompensation;
            float effectiveDrag;
            if (restitution > 0) {
                float portionWithMovement = movement.y / currentMovement.y;
                gravityCompensation = portionWithMovement * player.getEffectiveGravity();
                // TODO: Properly implement this, I can't figure this out, it def differ from Java.
                // We're currently hacking around this in UncertainRunner#resolveUncertainBouncing
                effectiveDrag = state instanceof BedBlockState ? 1.25f : 1f; // The value here is not correct, but whatever, it's just for the hacks.
                player.bounce = true;
            } else {
                gravityCompensation = 0;
                effectiveDrag = 1;
            }

            movementAfterBounce.y = (gravityCompensation - currentMovement.y) * effectiveDrag * restitution;
            player.minBounceYVel = (gravityCompensation - currentMovement.y) * 0.91f * restitution; // 0.91 is a bit ehhhhhh but sure.
        }

        player.velocity = movementAfterBounce;
    }
}
