package ac.boar.anticheat.packets.input.teleport;

import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.data.input.TickData;
import ac.boar.anticheat.packets.input.legacy.LegacyAuthInputPackets;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.PredictionRunner;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.teleport.data.TeleportCache;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;

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
            } else if (cache instanceof TeleportCache.Rewind rewind) {
                this.processRewind(player, rewind, packet);
            } else {
                throw new RuntimeException("Failed to process queued teleports, invalid teleport=" + cache);
            }
        }
    }

    private void processDimensionSwitch(final BoarPlayer player, final TeleportCache.DimensionSwitch dimension, final PlayerAuthInputPacket packet) {
        // Dimension switch should be followed with teleport so we don't have to do resync if the position mismatch.
        if (packet.getPosition().distance(dimension.getPosition().toVector3f()) <= 1.0E-3F) {
            player.setPos(new Vec3(packet.getPosition().sub(0, player.getYOffset(), 0)));
            player.unvalidatedPosition = player.prevUnvalidatedPosition = player.position.clone();

            player.velocity = Vec3.ZERO.clone();
            player.predictionResult = new PredictionData(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
        }
    }

    private void processTeleport(final BoarPlayer player, final TeleportCache.Normal normal, final PlayerAuthInputPacket packet) {
        float distance = packet.getPosition().distance(normal.getPosition().toVector3f());
        // I think I'm being a bit lenient but on Bedrock the position error seems to be a bit high.
        if (packet.getInputData().contains(PlayerAuthInputData.HANDLE_TELEPORT) && distance <= 1.0E-3F) {
            player.setPos(new Vec3(packet.getPosition().sub(0, player.getYOffset(), 0)));
            player.unvalidatedPosition = player.prevUnvalidatedPosition = player.position.clone();

            if (player.unvalidatedTickEnd.lengthSquared() > 1.0E-6 && player.bestPossibility.getType() == VectorType.VELOCITY) {
                player.velocity = player.bestPossibility.getVelocity();
            } else {
                player.velocity = Vec3.ZERO.clone();
            }
            player.predictionResult = new PredictionData(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO); // Yep!

            // This value can be true but since Geyser always send false then it is always false.
            player.onGround = false;
        } else {
            // Player rejected teleport OR this is not the latest teleport.
            if (!player.getTeleportUtil().isTeleporting()) {
                player.getTeleportUtil().teleportTo(normal);
            }
        }
    }

    // Wouldn't it be nice to provide us a way to know when player accept rewind mojang :(
    // There will be edge cases where player lag right after accepting the stack id responsible for rewind... maaaaaaaybe
    // we could check for current offset and if it's close then player might possibility HAVEN'T received the rewind yet?
    // Just ignore the edge cases for now.
    private void processRewind(final BoarPlayer player, final TeleportCache.Rewind rewind, final PlayerAuthInputPacket packet) {
        if (player.getTeleportUtil().isTeleporting()) {
            return; // nah.
        }

        if (player.isMovementExempted()) { // Fully exempted from rewind teleport.
            return;
        }

        player.onGround = rewind.isOnGround();
        player.velocity = rewind.getTickEnd();
        player.setPos(rewind.getPosition().subtract(0, player.getYOffset(), 0));
        player.prevUnvalidatedPosition = player.unvalidatedPosition = player.position.clone();

        player.getTeleportUtil().cachePosition(rewind.getTick(), rewind.getPosition().toVector3f());

        // Rewind can cause some problem if the tick we use have a different bounding width/height, so this is a bit of a hack but welp.
        final SessionPlayerEntity entity = player.getSession().getPlayerEntity();
        entity.getDirtyMetadata().put(EntityDataTypes.WIDTH, entity.getBoundingBoxWidth());
        entity.getDirtyMetadata().put(EntityDataTypes.HEIGHT, entity.getBoundingBoxHeight());
        entity.updateBedrockMetadata();

        // Keep running prediction until we catch up with the player current tick.
        long currentTick = rewind.getTick();
        while (currentTick != player.tick) {
            if (currentTick != rewind.getTick() && player.position.distanceTo(player.unvalidatedPosition) > player.getMaxOffset()) {
                player.unvalidatedPosition = player.position.clone();
            }

            currentTick++;

            if (currentTick == player.tick) {
                LegacyAuthInputPackets.processAuthInput(player, packet, true);
                LegacyAuthInputPackets.updateUnvalidatedPosition(player, packet);
            } else if (player.getTeleportUtil().getAuthInputHistory().containsKey(currentTick)) {
                final TickData data = player.getTeleportUtil().getAuthInputHistory().get(currentTick);
                LegacyAuthInputPackets.processAuthInput(player, data.packet(), false);
                LegacyAuthInputPackets.updateUnvalidatedPosition(player, packet);

                // Reverted back to the old flags and dimensions.
                player.getFlagTracker().set(player, data.flags(), false);
                // TODO: Is this really the case.
                player.dimensions = data.dimensions();
            }

            new PredictionRunner(player).run();
            // player.getTeleportUtil().cachePosition(currentTick, player.position.add(0, player.getYOffset(), 0).toVector3f());
        }
    }
}
