package ac.boar.anticheat.prediction.engine.impl.fluid;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.data.enchantment.Enchantment;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.data.BounceGravityCorrection;
import ac.boar.anticheat.prediction.engine.base.PredictionEngine;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.Map;

public class WaterPredictionEngine extends PredictionEngine {

    private float strideLevel;

    public WaterPredictionEngine(BoarPlayer player) {
        super(player);
    }

    @Override
    public Vec3 travel(Vec3 vec3) {
        ItemData boostSlot = player.compensatedInventory.armorContainer.get(3).getData();
        Map<Enchantment, Integer> enchantments = CompensatedInventory.getEnchantments(boostSlot);
        Integer depthStrider = enchantments.get(Enchantment.DEPTH_STRIDER);

        // WaterTravelSystem::doTickWaterTravelSystem
        this.strideLevel = depthStrider == null ? 0 : MathUtil.clamp(depthStrider, 0, 3);
        final float level = this.effectiveStrideLevel();
        final float speed = level > 0 ? 0.02F + ((player.getSpeed() - 0.02F) * level) / 3.0F : 0.02F;

        player.getMovementTrace().log("water: depthStrider=" + depthStrider + " accelLevel=" + level + " speed=" + speed);
        return this.moveRelative(vec3, speed);
    }

    @Override
    public void finalizeMovement() {
        this.finalizeMovement(null);
    }

    @Override
    public void finalizeMovement(final BounceGravityCorrection correction) {
        boolean sprinting = player.getFlagTracker().has(EntityFlag.SPRINTING);

        // Yep, on bedrock the player can move fast in water just by sprinting, not swimming, and they can sprint in water yay!
        // This was natively fixed in 1.21.80 but then the fix was removed in 1.21.81 (lol), so if you want to support
        // any version below 1.21.90, and if the version is >= 1.21.80 and < 1.21.90 then you will have to bruteforce to
        // see if player is actually water sprinting or not, since there is no actual way to tell.
        // MobMovementDrag::tickApplyWaterDrag reads the SPRINTING data flag so a sprint-swimming client keeps that flag set
        // for the whole swim, in every stick direction
        boolean fastTickEnd = sprinting || player.getInputData().contains(PlayerAuthInputData.STOP_SWIMMING);

        float f = fastTickEnd ? 0.9F : 0.8F;
        f += (0.54600006f - f) * (this.effectiveStrideLevel() / 3.0F);

        player.velocity = player.velocity.multiply(f, 0.8F, f);
        player.velocity = this.getFluidFallingAdjustedMovement(player.getEffectiveGravity(), player.velocity, correction);
    }

    // ref WaterTravelSystem::doTickWaterTravelSystem and MobMovementDrag::tickApplyWaterDrag
    private float effectiveStrideLevel() {
        return player.onGround ? this.strideLevel : this.strideLevel * 0.5F;
    }

    private Vec3 getFluidFallingAdjustedMovement(float gravity, Vec3 motion, BounceGravityCorrection correction) {
        if (player.hasEffect(Effect.LEVITATION)) {
            float y = motion.y + (((player.getEffect(Effect.LEVITATION).getAmplifier() + 1) * 0.05F) - motion.y) * 0.2F;
            return new Vec3(motion.x, y, motion.z);
        }

        // ViewT<StrictEntityContext, Include<WaterTravelFlagComponent, PlayerComponent>, Exclude<LevitateTravelFlagComponent>, ActorDataFlagComponent const>::each<void (*)(StrictEntityContext const&, ActorDataFlagComponent const&, EntityModifier<ApplyGravityComponent>), EntityModifier<ApplyGravityComponent>&>
        if (gravity != 0.0 && !player.getFlagTracker().has(EntityFlag.SWIMMING)) {
            return new Vec3(motion.x, BounceGravityCorrection.applyGravity(motion.y, -0.005F, correction), motion.z);
        }

        return motion;
    }
}
