package ac.boar.anticheat.packets.input.teleport;

import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.prediction.ticker.base.EntityTicker;
import ac.boar.anticheat.teleport.data.TeleportData;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.Queue;

public class TeleportHandler {
    protected void processQueuedTeleports(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        final Queue<TeleportData> queuedTeleports = player.getTeleportUtil().getQueuedTeleports();

        if (queuedTeleports.isEmpty()) {
            return;
        }

        TeleportData lastAccepted = null;
        TeleportData data;
        while ((data = queuedTeleports.peek()) != null) {
            if (!data.isAccepted()) { // Teleport should be in order, which means no way the next one is accepted.
                break;
            }

            queuedTeleports.poll();

            // Bedrock don't reply to teleport individually using a separate tick packet instead it just simply set its position to
            // the teleported position and then let us know the *next tick*, so we do the same!
            this.processTeleport(player, data, packet);
            lastAccepted = data;
        }

        if (lastAccepted != null && lastAccepted.getSource() == TeleportData.Source.MOVE_PLAYER_TELEPORT) {
            // ref LiquidPhysicsSystem::_liquidBlockFetch and StrictTickingSystemFunctionAdapter<&MobTravelTeleportedFilterSystem::tick>::tick.
            // calculate one push at the final accepted teleport
            new EntityTicker(player).applyWaterPushAfterTeleport();
            player.predictionResult = new PredictionData(Vec3.ZERO, Vec3.ZERO, player.velocity.clone());
            player.lastTickFinalVelocity = player.velocity.clone();
        }
    }

    private void processTeleport(final BoarPlayer player, final TeleportData data, final PlayerAuthInputPacket packet) {
        player.setPos(data.getPosition().down(player.getYOffset()));
        player.unvalidatedPosition = player.prevUnvalidatedPosition = player.position.clone();
        player.unvalidatedNativeOriginY = player.nativeOriginY;
        player.velocity = Vec3.ZERO.clone();
        player.certainVelocity = null;
        player.predictionResult = new PredictionData(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
        player.onGround = data.isOnGround();
        player.getTeleportUtil().updateLastKnownValid(data.getPosition());
        player.getTeleportUtil().markCorrected();
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
