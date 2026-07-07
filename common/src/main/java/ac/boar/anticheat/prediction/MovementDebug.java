package ac.boar.anticheat.prediction;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.config.Config;
import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.branch.MovementBranch;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

public final class MovementDebug {
    private MovementDebug() {}

    public static boolean enabled() {
        final Config config = Boar.getConfig();
        return config != null && config.debugMode();
    }

    public static void log(final BoarPlayer player, final String stage, final String details) {
        if (!enabled()) {
            return;
        }

        Boar.debug("[movement-debug] tick=" + player.tick + " [" + stage + "] " + details, Boar.DebugMessage.INFO);
    }

    public static void branch(final BoarPlayer player, final int id, final MovementBranch branch) {
        log(player, "BRANCH", "id=" + id + " " + branch.describe());
    }

    // Full-precision vector formatting; movement bugs hinge on tiny deltas so we never round here.
    public static String vec(final Vec3 vec3) {
        if (vec3 == null) {
            return "null";
        }

        return "(" + vec3.x + ", " + vec3.y + ", " + vec3.z + ")";
    }

    public static String flags(final BoarPlayer player) {
        return "onGround=" + player.onGround
                + " hCollision=" + player.horizontalCollision
                + " vCollision=" + player.verticalCollision
                + " touchingWater=" + player.touchingWater
                + " inLava=" + player.isInLava()
                + " waterHeight=" + player.getFluidHeight(Fluid.WATER)
                + " lavaHeight=" + player.getFluidHeight(Fluid.LAVA)
                + " sprinting=" + player.getFlagTracker().has(EntityFlag.SPRINTING)
                + " sneaking=" + player.getFlagTracker().has(EntityFlag.SNEAKING)
                + " swimming=" + player.getFlagTracker().has(EntityFlag.SWIMMING)
                + " gliding=" + player.getFlagTracker().has(EntityFlag.GLIDING)
                + " usingItem=" + player.getFlagTracker().has(EntityFlag.USING_ITEM)
                + " climbable=" + player.onClimbable()
                + " soulSandBelow=" + player.soulSandBelow
                + " fallDistance=" + player.fallDistance;
    }
}
