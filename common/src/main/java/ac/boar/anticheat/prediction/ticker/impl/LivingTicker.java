package ac.boar.anticheat.prediction.ticker.impl;

import ac.boar.anticheat.collision.util.CuboidBlockIterator;
import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.enchantment.Enchantment;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.MovementDebug;
import ac.boar.anticheat.prediction.engine.base.PredictionEngine;
import ac.boar.anticheat.prediction.engine.impl.GlidingPredictionEngine;
import ac.boar.anticheat.prediction.engine.impl.GroundAndAirPredictionEngine;
import ac.boar.anticheat.prediction.engine.impl.fluid.LavaPredictionEngine;
import ac.boar.anticheat.prediction.engine.impl.fluid.WaterPredictionEngine;
import ac.boar.anticheat.prediction.ticker.base.EntityTicker;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.block.Blocks;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.TrigMath;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.List;

public class LivingTicker extends EntityTicker {
    public LivingTicker(BoarPlayer player) {
        super(player);
    }

    @Override
    public void tick() {
        super.tick();

        if (player.dirtyRiptide && player.getInputData().contains(PlayerAuthInputData.START_SPIN_ATTACK)) {
            player.getFlagTracker().set(EntityFlag.DAMAGE_NEARBY_MOBS, true);
            // System.out.println("Trying to riptide.");

            int i = CompensatedInventory.getEnchantments(player.riptideItem).get(Enchantment.RIPTIDE);
            float f = 1.5f + 0.75F * (i - 1);

            float g = player.rotation.getY();
            float h = player.rotation.getX();
            float k = -TrigMath.sin(g * (MathUtil.DEGREE_TO_RAD)) * TrigMath.cos(h * (MathUtil.DEGREE_TO_RAD));
            float l = -TrigMath.sin(h * (MathUtil.DEGREE_TO_RAD));
            float m = TrigMath.cos(g * (MathUtil.DEGREE_TO_RAD)) * TrigMath.cos(h * (MathUtil.DEGREE_TO_RAD));
            float n = (float) GenericMath.sqrt(k * k + l * l + m * m);

            player.velocity = player.velocity.add(k * (f / n), l * (f / n), m * (f / n));
            player.autoSpinAttackTicks = 20;
//            if (player.onGround) {
//                this.doSelfMove(new Vec3(0, 1.1999999284744263F, 0));
//                player.prevUnvalidatedPosition = player.position.clone();
//            }

            player.thisTickSpinAttack = true;
            player.thisTickOnGroundSpinAttack = player.onGround;
        }

        this.aiStep();
    }

    public void aiStep() {
        // Note: There is no 0.003 movement limiter on Bedrock, I think.
        // But there seems to be one for extremely small movement.
        player.velocity.x = Math.abs(player.velocity.x) < 1.0E-8 ? 0 : player.velocity.x;
        player.velocity.y = Math.abs(player.velocity.y) < 1.0E-8 ? 0 : player.velocity.y;
        player.velocity.z = Math.abs(player.velocity.z) < 1.0E-8 ? 0 : player.velocity.z;

        this.applyInput();

        if (MovementDebug.enabled()) {
            MovementDebug.log(player, "AI-STEP", "afterInput input=" + MovementDebug.vec(player.input)
                    + " velAfterClean=" + MovementDebug.vec(player.velocity)
                    + " frictionInfluencedSpeed=" + player.getFrictionInfluencedSpeed(player.onGround
                            ? player.compensatedWorld.getBlockState(player.getBlockPosBelowThatAffectsMyMovement(), 0).getFriction() : 1.0f));
        }

        // Prevent player from "elytra bouncing".
        if (player.getFlagTracker().has(EntityFlag.GLIDING) && player.onGround && player.getInputData().contains(PlayerAuthInputData.START_JUMPING)) {
            player.getTeleportUtil().rewind(player.tick - 1);
        }

        boolean inScaffolding = false, onScaffolding = false;
        final CuboidBlockIterator iterator = CuboidBlockIterator.iterator(player.boundingBox);
        while (iterator.step()) {
            int x = iterator.getX(), y = iterator.getY(), z = iterator.getZ();
            if (player.compensatedWorld.isChunkLoaded(x, z)) {
                int flooredY = GenericMath.floor(player.position.y);
                BoarBlockState state = player.compensatedWorld.getBlockState(x, y, z, 0);
                if (state.is(Blocks.SCAFFOLDING)) {
                    if (y == flooredY && player.boundingBox.intersects(x, y, z, x + 1, y + 1, z + 1)) {
                        inScaffolding = true;
                    } else if (y + 1 == flooredY && player.boundingBox.offset(0, -1, 0).intersects(x, y, z, x + 1, y + 1, z + 1)) {
                        onScaffolding = true;
                    }
                }
            }
        }

        final Vec3 velBeforeJump = MovementDebug.enabled() ? player.velocity.clone() : null;
        if (inScaffolding && player.unvalidatedPosition.subtract(player.prevUnvalidatedPosition).y > 0) {
            if (player.getInputData().contains(PlayerAuthInputData.JUMPING) || player.getInputData().contains(PlayerAuthInputData.ASCEND_BLOCK)) {
                player.velocity.y = 0.15F;
            }
        } else {
            player.velocity = player.jump(player.velocity);
        }

        if (MovementDebug.enabled()) {
            MovementDebug.log(player, "JUMP", "inScaffolding=" + inScaffolding + " onScaffolding=" + onScaffolding
                    + " jumpPower=" + player.getJumpPower() + " jumpBoost=" + player.getJumpBoostPower()
                    + " velBefore=" + MovementDebug.vec(velBeforeJump) + " velAfter=" + MovementDebug.vec(player.velocity));
        }

        boolean descending = player.getInputData().contains(PlayerAuthInputData.SNEAKING) || player.getInputData().contains(PlayerAuthInputData.DESCEND_BLOCK);

        player.scaffoldDescend = false;
        BoarBlockState state = player.compensatedWorld.getBlockState(player.getOnPos(1F), 0);
        if (descending) {
            if (state.is(Blocks.POWDER_SNOW) ||  player.getInBlockState().is(Blocks.POWDER_SNOW)) {
                player.velocity.y = -0.15F;
            }

            if (onScaffolding && Math.abs(player.unvalidatedTickEnd.y) - 0.15F < 0.01F) {
                player.velocity.y = -0.15F;
                player.scaffoldDescend = true;
            }

            if (MovementDebug.enabled()) {
                MovementDebug.log(player, "DESCEND", "powderSnow=" + (state.is(Blocks.POWDER_SNOW) || player.getInBlockState().is(Blocks.POWDER_SNOW))
                        + " scaffoldDescend=" + player.scaffoldDescend + " velY=" + player.velocity.y);
            }
        }

        if (player.getFlagTracker().has(EntityFlag.GLIDING) && (player.onGround || player.vehicleData != null || player.hasEffect(Effect.LEVITATION))) {
            player.getFlagTracker().set(EntityFlag.GLIDING, false);
        }

        player.cachedOnPos = null;

        Box oldBox = player.boundingBox.clone();

        this.travelRidden();
        this.applyEffectsFromBlocks();

        if (player.autoSpinAttackTicks > 0) {
            --player.autoSpinAttackTicks;
            this.checkAutoSpinAttack(oldBox, player.boundingBox);
        }
    }

    protected final void checkAutoSpinAttack(Box aABB, Box aABB2) {
        Box aABB3 = aABB.union(aABB2);

        List<EntityCache> list = player.compensatedWorld.getEntities().values().stream().toList();
        if (!list.isEmpty()) {
            for (EntityCache entity : list) {
                if (entity.getCurrent() == null) {
                    continue;
                }

                if (!entity.getCurrent().getBoundingBox().intersects(aABB3)) {
                    continue;
                }

                player.dirtySpinStop = true;
                // player.autoSpinAttackTicks = 0;
                // player.velocity = player.velocity.multiply(-0.2F);
                break;
            }
        } else if (player.horizontalCollision) {
            player.stopRiptide();
        }

        if (player.autoSpinAttackTicks <= 0) {
            player.stopRiptide();
        }
    }

    protected void travelRidden() {
//        Vec3 vec32 = this.getRiddenInput(player, vec3);
//        this.tickRidden(player, vec32);
//        if (this.canSimulateMovement()) {
//            this.setSpeed(this.getRiddenSpeed(player));
//            this.travel(vec32);
//        } else {
//            this.setDeltaMovement(Vec3.ZERO);
//        }

        this.travel();
    }

    public void applyInput() {
        player.input = player.input.multiply(0.98F);
    }

    protected void travel() {
        if (player.isInLava() || player.touchingWater) {
            MovementDebug.log(player, "TRAVEL", "engine=FLUID (" + (player.touchingWater ? "WATER" : "LAVA")
                    + ") velIn=" + MovementDebug.vec(player.velocity));
            this.travelInFluid();
        } else if (player.getFlagTracker().has(EntityFlag.GLIDING)) {
            MovementDebug.log(player, "TRAVEL", "engine=GLIDING velIn=" + MovementDebug.vec(player.velocity));
//            if (this.onClimbable()) {
//                this.travelInAir(vec3);
//                this.stopFallFlying();
//                return;
//            }

            player.velocity = new GlidingPredictionEngine(player).travel(player.velocity);
            MovementDebug.log(player, "TRAVEL", "GLIDING velAfterEngine=" + MovementDebug.vec(player.velocity)
                    + " glideBoostTicks=" + player.glideBoostTicks);
            this.doSelfMove(player.velocity.clone()); // this.move(MoverType.SELF, this.getDeltaMovement());
        } else {
            travelInAir();
        }
    }

    private void travelInAir() {
        final PredictionEngine engine = new GroundAndAirPredictionEngine(player);
        player.velocity = engine.travel(player.velocity);
        MovementDebug.log(player, "TRAVEL", "GROUND_AIR velAfterEngine(input applied)=" + MovementDebug.vec(player.velocity)
                + " gravity=" + player.getEffectiveGravity());
        this.doSelfMove(player.velocity.clone()); // this.move(MoverType.SELF, this.getDeltaMovement());
        engine.finalizeMovement();
        MovementDebug.log(player, "FINALIZE", "GROUND_AIR velAfterFinalize(gravity+friction)=" + MovementDebug.vec(player.velocity));
    }

    private void travelInFluid() {
        float d = player.position.y;
        final PredictionEngine engine;
        if (player.touchingWater) {
            engine = new WaterPredictionEngine(player);
        } else {
            engine = new LavaPredictionEngine(player);
        }
        player.velocity = engine.travel(player.velocity);
        MovementDebug.log(player, "TRAVEL", (player.touchingWater ? "WATER" : "LAVA")
                + " velAfterEngine(input applied)=" + MovementDebug.vec(player.velocity)
                + " hasDepthStrider=" + player.hasDepthStrider);
        this.doSelfMove(player.velocity.clone());
        engine.finalizeMovement();
        MovementDebug.log(player, "FINALIZE", (player.touchingWater ? "WATER" : "LAVA")
                + " velAfterFinalize(gravity+friction)=" + MovementDebug.vec(player.velocity));

        Vec3 vec33 = player.velocity;
        if (player.horizontalCollision && player.doesNotCollide(vec33.x, vec33.y + 0.6f - player.position.y + d, vec33.z)) {
            player.velocity.y = 0.3F;
            MovementDebug.log(player, "FINALIZE", "FLUID climb-out bump velY->0.3");
        }
    }
}
