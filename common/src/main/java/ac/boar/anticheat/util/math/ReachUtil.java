package ac.boar.anticheat.util.math;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.compensated.cache.entity.state.CachedEntityState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.Pair;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;

public class ReachUtil {
    private static final int STEPS = 10;
    private static final float STEPS_INC = 1f / STEPS;

    private static final float REACH_RAY_LENGTH = 7F;
    private static final float BOX_EXPANSION = 0.1F;

    public static float calculateReach(
            final BoarPlayer player,
            final Pair<Vec3, Vec3> attackPositions,
            final EntityCache entity,
            final Pair<Vec3, Vec3> prevTickEntityPositions
    ) {
        final CachedEntityState entityState = entity.getCurrent();
        float distanceSq = Float.MAX_VALUE;

        // If the player is on touch controls and not using a crosshair, we don't have to interpolate the rotations when calculating reach.
        final boolean isLikelyTouchSnap = player.inputMode == InputMode.TOUCH &&
                (player.interactionModel == InputInteractionModel.TOUCH || (player.interactionModel == InputInteractionModel.CLASSIC && player.prevInteractRotUnchanged));

        for (float f = 0; f <= 1f; f += STEPS_INC) {
            final Vec3 rotationVec = getRotationVector(player, isLikelyTouchSnap ? 1.0f : f);
            final Vec3 reachStart = getEyePosition(player, attackPositions, f);
            final Vec3 reachEnd = reachStart.add(rotationVec.multiply(REACH_RAY_LENGTH));

            final Vec3 primaryEntityPos = lerp(f, prevTickEntityPositions.a(), prevTickEntityPositions.b());
            final Box primaryBox = entityState.calculateBoundingBox(primaryEntityPos);
            final Vec3 primaryHit = calculateHitResult(primaryBox, reachStart, reachEnd);
            if (primaryHit != null) {
                distanceSq = Math.min(distanceSq, primaryHit.squaredDistanceTo(reachStart));
            }

            final Box altBox = entityState.getBoundingBox(f);
            final Vec3 altHit = calculateHitResult(altBox, reachStart, reachEnd);
            if (altHit != null) {
                distanceSq = Math.min(distanceSq, altHit.squaredDistanceTo(reachStart));
            }
        }

        return distanceSq == Float.MAX_VALUE ? distanceSq : (float) Math.sqrt(distanceSq);
    }

    private static Vec3 calculateHitResult(final Box box, final Vec3 min, final Vec3 max) {
        Box expanded = box.expand(BOX_EXPANSION);
        if (expanded.contains(min)) {
            return min;
        }
        return expanded.clip(min, max).orElse(null);
    }

    private static Vec3 lerp(float f, Vec3 a, Vec3 b) {
        return new Vec3(
                MathUtil.lerp(f, a.x, b.x),
                MathUtil.lerp(f, a.y, b.y),
                MathUtil.lerp(f, a.z, b.z)
        );
    }

    private static Vec3 getRotationVector(BoarPlayer player, float f) {
        return MathUtil.getRotationVector(
                MathUtil.lerp(f, player.prevInteractRotation.getX(), player.interactRotation.getX()),
                MathUtil.lerp(f, player.prevInteractRotation.getY(), player.interactRotation.getY())
        );
    }

    private static Vec3 getEyePosition(BoarPlayer player, Pair<Vec3, Vec3> pair, float f) {
        float lerpX = MathUtil.lerp(f, pair.a().x, pair.b().x);
        float lerpY = MathUtil.lerp(f, pair.a().y, pair.b().y) + player.dimensions.eyeHeight();
        float lerpZ = MathUtil.lerp(f, pair.a().z, pair.b().z);
        return new Vec3(lerpX, lerpY, lerpZ);
    }
}
