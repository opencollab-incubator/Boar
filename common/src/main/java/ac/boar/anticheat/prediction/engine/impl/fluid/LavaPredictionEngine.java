package ac.boar.anticheat.prediction.engine.impl.fluid;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.data.BounceGravityCorrection;
import ac.boar.anticheat.prediction.engine.base.PredictionEngine;
import ac.boar.anticheat.util.math.Vec3;

public class LavaPredictionEngine extends PredictionEngine {
    public LavaPredictionEngine(BoarPlayer player) {
        super(player);
    }

    @Override
    public Vec3 travel(Vec3 vec3) {
        return this.moveRelative(vec3, 0.02F);
    }

    @Override
    public void finalizeMovement() {
        this.finalizeMovement(null);
    }

    @Override
    public void finalizeMovement(final BounceGravityCorrection correction) {
        float gravity = player.getEffectiveGravity();
        player.velocity = player.velocity.multiply(0.5F);

        if (gravity != 0.0) {
            // MobMovementGravity::tickLavaGravity
            player.velocity.y = BounceGravityCorrection.applyGravity(player.velocity.y, -0.02F, correction);
        }
    }
}