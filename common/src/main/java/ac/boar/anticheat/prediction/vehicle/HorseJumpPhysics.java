package ac.boar.anticheat.prediction.vehicle;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

/** Horse jump math. The float operation order was measured on Education 1.26.30.1. The launch reads MathUtil's sine table. */
public final class HorseJumpPhysics {
    private HorseJumpPhysics() {}

    public static boolean hasControlFlag(final EntityCache horse) {
        return horse != null && (horse.hasFlag(EntityFlag.WASD_CONTROLLED) || horse.hasFlag(EntityFlag.WASD_AIR_CONTROLLED));
    }

    public static float scale(final int request) {
        if (request < 0) {
            throw new IllegalArgumentException("Negative horse jump request");
        }
        return request >= 90 ? 1.0F : (((float) request * 0.4F) / 90.0F) + 0.4F;
    }

    public static float turn(final float yaw, final float intentYaw) {
        final float diff = MathUtil.wrapYaw(intentYaw - yaw);
        final float k = Math.max(0.18F, (45.0F - Math.min(Math.abs(diff), 45.0F)) / 90.0F);
        return MathUtil.wrapYaw(yaw + diff * (k * 0.7F));
    }

    // The jump launch. jumpScale: the charged jump power (0 to 1). blockFactor: 0.6 on a block that prevents jumps (honey), else 1.
    // boost: the jump boost effect part. attribute: the horse's jump strength attribute.
    public static Vec3 launch(final Vec3 velocity, final float attribute, final float jumpScale, final float boost, final float blockFactor, final float yaw,
                              final float forward) {
        float x = velocity.x, z = velocity.z;
        final float y = blockFactor * boost + blockFactor * (attribute * jumpScale);
        if (forward > 0) {
            final float a = yaw * MathUtil.DEGREE_TO_RAD;
            final float t = a * 10430.378F;
            x -= (MathUtil.sin(t) * 0.4F) * jumpScale;
            z += (MathUtil.sin(t + 16384.0F) * 0.4F) * jumpScale;
        }
        return new Vec3(x, y, z);
    }
}
