package ac.boar.anticheat.prediction.engine.impl;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.base.PredictionEngine;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.anticheat.util.MathUtil;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.TrigMath;

public class GlidingPredictionEngine extends PredictionEngine {
    public GlidingPredictionEngine(final BoarPlayer player) {
        super(player);
    }

    @Override
    public Vec3 travel(Vec3 movement) {
        final Vec3 lookAngle = MathUtil.getRotationVector(player.pitch, player.yaw);

        // Based off https://mcsrc.dev/1/26.1.2/net/minecraft/world/entity/LivingEntity#L2536
        float leanAngle = player.pitch * MathUtil.DEGREE_TO_RAD;
        float lookHorLength = lookAngle.horizontalLength();
        float moveHorLength = movement.horizontalLength();

        // Lift force is kinda the only part somewhat differ from java, according to BDS?
        float cosOfLeanAngle = TrigMath.cos(leanAngle);
        float liftForce = cosOfLeanAngle * (Math.min((float) GenericMath.sqrt(lookAngle.lengthSquared()) * 2.5F, 1.0F) * cosOfLeanAngle);

        movement.y -= (liftForce * 0.75F - 1.0F) * -player.getEffectiveGravity(movement);
        if (movement.y < 0.0 && lookHorLength > 0.0) {
            float convert = movement.y * -0.1F * liftForce;
            movement = movement.add(lookAngle.x * convert / lookHorLength, convert, lookAngle.z * convert / lookHorLength);
        }
        if (leanAngle < 0.0) {
            float convert = TrigMath.sin(leanAngle) * moveHorLength * -0.04f;
            movement = movement.subtract(convert * lookAngle.x / lookHorLength, convert * -3.2F, convert * lookAngle.z / lookHorLength);
        }
        if (lookHorLength > 0.0) {
            movement = movement.add(
                    (lookAngle.x / lookHorLength * moveHorLength - movement.x) * 0.1f, 0, (lookAngle.z / lookHorLength * moveHorLength - movement.z) * 0.1f
            );
        }

        // It seems like it's the same as java code (https://mcsrc.dev/1/26.1.2/net/minecraft/world/entity/projectile/FireworkRocketEntity#L136)
        // Although different is that it's ticking here instead of in the fireworks entity (since the fireworks entity logic doesn't really exist)
        if (player.glideBoostTicks > 0) {
            movement = movement.add(lookAngle.x * 0.1f + (lookAngle.x * 1.5f - movement.x) * 0.5f,
                    lookAngle.y * 0.1f + (lookAngle.y * 1.5f - movement.y) * 0.5f,
                    lookAngle.z * 0.1f + (lookAngle.z * 1.5f - movement.z) * 0.5f);
        }

        return movement.multiply(0.99f, 0.98f, 0.99f);
    }

    @Override
    public void finalizeMovement() {}
}
