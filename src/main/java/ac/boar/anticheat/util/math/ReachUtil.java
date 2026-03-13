package ac.boar.anticheat.util.math;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.Pair;
import org.cloudburstmc.protocol.bedrock.data.InputMode;

public class ReachUtil {
    public static float calculateReach(final BoarPlayer player, final Pair<Vec3, Vec3> pair, final EntityCache entity) {
        float distance = Float.MAX_VALUE;

        final float MAX_RANGE = MathUtil.square(Boar.getConfig().toleranceReach());
        float deltaTicks = 0;

        while (deltaTicks < 1 + 1.0E-3) {
            final Vec3 rotationVec = getRotationVector(player, deltaTicks);
            final Vec3 min = getEyePosition(player, pair, deltaTicks);
            final Vec3 max = min.add(rotationVec.multiply(7F));

            final Vec3 hitResult = calculateHitResult(entity.getCurrent().calculateBoundingBox(), min, max);
            if (hitResult != null) {
                distance = Math.min(distance, hitResult.squaredDistanceTo(min));
            }

            // We're unsure due to split latency, let's try both past and current position to see what have the closer position.
            if (distance > MAX_RANGE && entity.getPast() != null) {
                final Vec3 prevHitResult = calculateHitResult(entity.getPast().calculateBoundingBox(), min, max);
                if (prevHitResult != null) {
                    distance = Math.min(distance, prevHitResult.squaredDistanceTo(min));
                }
            }

            // Valid hit, let exit to save performance!
            if (distance <= MAX_RANGE) {
                break;
            }

            deltaTicks += 0.01f;
        }

        return distance == Float.MAX_VALUE ? distance : (float) Math.sqrt(distance);
    }

    private static Vec3 calculateHitResult(final Box box, final Vec3 min, final Vec3 max) {
        Box lv5 = box.expand(0.1F);
        if (lv5.contains(min)) {
            return min;
        }
        return lv5.clip(min, max).orElse(null);
    }

    private static Vec3 getRotationVector(BoarPlayer player, float f) {
        return MathUtil.getRotationVector(
                player.inputMode == InputMode.TOUCH ? player.interactRotation.getX() : MathUtil.lerp(f, player.prevPitch, player.pitch),
                player.inputMode == InputMode.TOUCH ? player.interactRotation.getY() : MathUtil.lerp(f, player.prevYaw, player.yaw)
        );
    }

    private static Vec3 getEyePosition(BoarPlayer player, Pair<Vec3, Vec3> pair, float f) {
        float d = MathUtil.lerp(f, pair.a().x, pair.b().x);
        float e = MathUtil.lerp(f, pair.a().y, pair.b().y) + player.dimensions.eyeHeight();
        float g = MathUtil.lerp(f, pair.a().z, pair.b().z);
        return new Vec3(d, e, g);
    }
}
