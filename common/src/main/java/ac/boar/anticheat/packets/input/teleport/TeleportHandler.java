package ac.boar.anticheat.packets.input.teleport;

import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.prediction.ticker.base.EntityTicker;
import ac.boar.anticheat.prediction.ticker.impl.PlayerTicker;
import ac.boar.anticheat.teleport.TeleportUtil;
import ac.boar.anticheat.teleport.data.TeleportData;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;

public class TeleportHandler {
    protected boolean processTeleport(final BoarPlayer player) {
        final TeleportUtil teleports = player.getTeleportUtil();
        final TeleportData data = teleports.takeAcceptedTeleport();
        if (data != null) {
            teleports.markCorrected();
            player.getMovementTrace().log("teleport: accepted source=" + data.getSource()
                    + " target=" + data.getPosition() + " onGround=" + data.isOnGround());
        }

        final int steps = teleports.getInterpolationTicks();
        if (steps > 0) {
            // MovementInterpolatorSystemImpl::_tickSystem clears velocity before each interpolation step
            final Vec3 target = teleports.getInterpolationTarget();
            final Vec3 origin = new Vec3(player.position.x, player.nativeOriginY, player.position.z);
            final Vec3 next = steps == 1 ? target.clone() : origin.add(target.subtract(origin).multiply(1.0F / steps));
            teleports.setPacketPosition(next);
            player.velocity = Vec3.ZERO.clone();
            player.certainVelocity = null;
            teleports.tickInterpolation();
            player.getMovementTrace().log("teleport: interpolation remaining=" + teleports.getInterpolationTicks() + " origin=" + next);
        }
        final boolean skipTravel = teleports.takeSkipTravel();
        final boolean applyWaterInput = teleports.takeApplyTeleportWaterInput();
        if (skipTravel) {
            if (player.certainVelocity != null) {
                player.velocity = player.certainVelocity.getVelocity().clone();
                player.certainVelocity = null;
            }
            if (applyWaterInput && new EntityTicker(player).applyWaterPushAfterTeleport()) {
                new PlayerTicker(player).applyWaterInputAfterTeleport();
            }
            player.bestPossibility = Vector.NONE;
            player.predictionResult = new PredictionData(Vec3.ZERO, Vec3.ZERO, player.velocity.clone());
            player.lastTickFinalVelocity = player.velocity.clone();
            teleports.updateLastKnownValid(new Vec3(player.position.x, player.nativeOriginY, player.position.z));
        }
        return skipTravel;
    }

    protected void processImmobile(BoarPlayer player) {
        player.velocity = Vec3.ZERO.clone();
        player.certainVelocity = null;
        player.predictionResult = new PredictionData(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
        player.bestPossibility = Vector.NONE;
    }

    protected void processExempted(BoarPlayer player) {
        player.setPos(player.unvalidatedPosition);
        player.nativeOriginY = player.unvalidatedNativeOriginY;

        // Clear velocity out manually since we haven't handled em.
        player.certainVelocity = null;

        // This is fine, we only need tick end and use before and after to calculate ground.
        player.predictionResult = new PredictionData(Vec3.ZERO, player.velocity.y < 0 && player.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION) ? new Vec3(0, 1, 0) : Vec3.ZERO, player.unvalidatedTickEnd);
        player.velocity = player.unvalidatedTickEnd.clone();

        player.bestPossibility = Vector.NONE;
    }
}
