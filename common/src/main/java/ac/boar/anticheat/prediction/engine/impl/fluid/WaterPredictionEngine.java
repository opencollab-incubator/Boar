package ac.boar.anticheat.prediction.engine.impl.fluid;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.data.enchantment.Enchantment;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.base.PredictionEngine;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.Map;

public class WaterPredictionEngine extends PredictionEngine {
    private float tickEndSpeed;
    public WaterPredictionEngine(BoarPlayer player) {
        super(player);
    }

    @Override
    public Vec3 travel(Vec3 vec3) {
        ItemData boostSlot = player.compensatedInventory.armorContainer.get(3).getData();
        Map<Enchantment, Integer> enchantments = CompensatedInventory.getEnchantments(boostSlot);
        Integer depthStrider = enchantments.get(Enchantment.DEPTH_STRIDER);

        float h = 0;
        if (depthStrider != null) {
            h = 0.33333334f + 0.33333334f * (float)(depthStrider - 1);
        }

        if (!player.onGround && player.getFlagTracker().has(EntityFlag.SWIMMING)) {
            h *= 0.5F;
        }

        this.tickEndSpeed = h;

        player.hasDepthStrider = this.tickEndSpeed > 0 && (!player.getFlagTracker().has(EntityFlag.SWIMMING) || depthStrider >= 4);
        return this.moveRelative(vec3, h > 0 ? 0.02F + ((player.getSpeed() - 0.02F) * h) : 0.02F);
    }

    @Override
    public void finalizeMovement() {
        if (!player.getFlagTracker().has(EntityFlag.SWIMMING) && !player.onGround) {
            this.tickEndSpeed *= 0.5F;
        }

        boolean sprinting = player.getFlagTracker().has(EntityFlag.SPRINTING);

        // Yep, on bedrock the player can move fast in water just by sprinting, not swimming, and they can sprint in water yay!
        // This was natively fixed in 1.21.80 but then the fix was removed in 1.21.81 (lol), so if you want to support
        // any version below 1.21.90, and if the version is >= 1.21.80 and < 1.21.90 then you will have to bruteforce to
        // see if player is actually water sprinting or not, since there is no actual way to tell.
        boolean fastTickEnd = sprinting || player.getInputData().contains(PlayerAuthInputData.STOP_SWIMMING);

        float f = fastTickEnd ? 0.9F : 0.8F;
        f += (0.54600006f - f) * this.tickEndSpeed;

        player.velocity = player.velocity.multiply(f, 0.8F, f);
        player.velocity = this.getFluidFallingAdjustedMovement(player.getEffectiveGravity(), player.velocity);
    }

    private Vec3 getFluidFallingAdjustedMovement(float gravity, Vec3 motion) {
        if (player.hasEffect(Effect.LEVITATION)) {
            float y = motion.y + (((player.getEffect(Effect.LEVITATION).getAmplifier() + 1) * 0.05F) - motion.y) * 0.2F;
            return new Vec3(motion.x, y, motion.z);
        }

        if (gravity != 0.0 && !player.getFlagTracker().has(EntityFlag.SWIMMING)) {
            return new Vec3(motion.x, motion.y - (gravity / 16.0F), motion.z);
        }

        return motion;
    }
}
