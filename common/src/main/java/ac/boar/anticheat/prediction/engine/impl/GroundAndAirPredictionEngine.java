package ac.boar.anticheat.prediction.engine.impl;

import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.data.vanilla.StatusEffect;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.base.PredictionEngine;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.util.block.specific.PowderSnowBlock;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.block.Blocks;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;

// Ground and Air, that how they call it in BDS I guess.
public class GroundAndAirPredictionEngine extends PredictionEngine {
    private float prevSlipperiness = 0.6F;

    public GroundAndAirPredictionEngine(BoarPlayer player) {
        super(player);
    }

    @Override
    public Vec3 travel(Vec3 vec3) {
        Vector3i blockPos = player.getBlockPosBelowThatAffectsMyMovement();
        this.prevSlipperiness = player.onGround ? player.compensatedWorld.getBlockState(blockPos, 0).getFriction() : 1.0f;
        return this.halfRelativeMovementCalculate(vec3, this.prevSlipperiness);
    }

    @Override
    public void finalizeMovement() {
        if (!player.scaffoldDescend || player.onGround) {
            final StatusEffect effect = player.getEffect(Effect.LEVITATION);
            if (effect != null) {
                player.velocity.y += (0.05f * (effect.getAmplifier() + 1) - player.velocity.y) * 0.2f;
            } else {
                player.velocity.y -= player.getEffectiveGravity();
            }
        }

        player.velocity.y *= 0.98F;

        // Seems to be the case here!
        if (player.horizontalCollision && (player.onClimbable() || player.getInBlockState().is(Blocks.POWDER_SNOW) && PowderSnowBlock.canEntityWalkOnPowderSnow(player))) {
            player.velocity.y = 0.2F;
        }

        float g = this.prevSlipperiness * 0.91F;
        player.velocity = player.velocity.multiply(g, 1, g);
    }

    private Vec3 halfRelativeMovementCalculate(Vec3 vec3, float f) {
        vec3 = this.moveRelative(vec3, player.getFrictionInfluencedSpeed(f));
        final boolean collidedOrJumping = player.horizontalCollision || player.getInputData().contains(PlayerAuthInputData.JUMPING);
        if (collidedOrJumping && (player.onClimbable() || player.getInBlockState().is(Blocks.POWDER_SNOW) && PowderSnowBlock.canEntityWalkOnPowderSnow(player))) {
            vec3.y = player.bestPossibility.getType() == VectorType.VELOCITY ? vec3.y : 0.2F;
        }

        return this.applyClimbingSpeed(vec3);
    }
}
