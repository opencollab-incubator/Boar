package ac.boar.anticheat.prediction.engine.base;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.block.Blocks;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

@RequiredArgsConstructor
public abstract class PredictionEngine {
    protected final BoarPlayer player;

    public abstract Vec3 travel(Vec3 vec3);
    public abstract void finalizeMovement();

    protected final Vec3 moveRelative(Vec3 delta, float f) {
        return delta.add(MathUtil.getInputVector(player.input, f, player.yaw));
    }

    protected final Vec3 applyClimbingSpeed(final Vec3 motion) {
        if (player.onClimbable()) {
            float g = Math.max(motion.y, -0.2F);
            if (g < 0.0 && !player.compensatedWorld.getBlockState(player.position.toVector3i(), 0).is(Blocks.SCAFFOLDING) && player.getFlagTracker().has(EntityFlag.SNEAKING)) {
                g = 0;
            }

            return new Vec3(motion.x, g, motion.z);
        }

        return motion;
    }
}
