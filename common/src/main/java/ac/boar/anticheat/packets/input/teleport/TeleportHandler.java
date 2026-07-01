package ac.boar.anticheat.packets.input.teleport;

import ac.boar.anticheat.data.input.PredictionResult;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.teleport.data.TeleportCache;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.Queue;

public class TeleportHandler {
    protected void processQueuedTeleports(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        final Queue<TeleportCache> queuedTeleports = player.getTeleportUtil().getQueuedTeleports();

        if (queuedTeleports.isEmpty()) {
            return;
        }

        TeleportCache cache;
        while ((cache = queuedTeleports.peek()) != null) {
            if (!cache.isAccepted()) {
                break;
            }

            queuedTeleports.poll();

//            TeleportCache peek = queuedTeleports.peek();
//            if (peek != null && peek.isAccepted()) {
//                continue;
//            }

            // Bedrock don't reply to teleport individually using a separate tick packet instead it just simply set its position to
            // the teleported position and then let us know the *next tick*, so we do the same!
            if (cache instanceof TeleportCache.Normal normal) {
                this.processTeleport(player, normal, packet);
            } else if (cache instanceof TeleportCache.DimensionSwitch dimension) {
                this.processDimensionSwitch(player, dimension, packet);
            } else {
                throw new RuntimeException("Failed to process queued teleports, invalid teleport=" + cache);
            }
        }
    }

    private void processDimensionSwitch(final BoarPlayer player, final TeleportCache.DimensionSwitch dimension, final PlayerAuthInputPacket packet) {
        player.setPos(new Vec3(dimension.getPosition().subtract(0, player.getYOffset(), 0).toVector3f()));
        player.unvalidatedPosition = player.prevUnvalidatedPosition = player.position.clone();
        player.velocity = Vec3.ZERO.clone();
        player.predictionResult = new PredictionResult(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
    }

    private void processTeleport(final BoarPlayer player, final TeleportCache.Normal teleport, final PlayerAuthInputPacket packet) {
        player.setPos(new Vec3(teleport.getPosition().subtract(0, player.getYOffset(), 0).toVector3f()));
        player.unvalidatedPosition = player.prevUnvalidatedPosition = player.position.clone();
        player.velocity = Vec3.ZERO.clone();
        player.predictionResult = new PredictionResult(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
        player.onGround = teleport.isOnGround();
    }
}
