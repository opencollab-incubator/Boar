package ac.boar.anticheat.packets.input.teleport;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.data.input.TickData;
import ac.boar.anticheat.packets.input.legacy.LegacyAuthInputPackets;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.PredictionRunner;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.teleport.data.RewindData;
import ac.boar.anticheat.teleport.data.TeleportData;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;

import java.util.Queue;

public class TeleportHandler {
    protected void processQueuedTeleports(final BoarPlayer player, final PlayerAuthInputPacket packet) {
        final Queue<TeleportData> queuedTeleports = player.getTeleportUtil().getQueuedTeleports();

        if (queuedTeleports.isEmpty()) {
            return;
        }

        TeleportData data;
        while ((data = queuedTeleports.peek()) != null) {
            if (!data.isAccepted()) { // Teleport should be in order, which means no way the next one is accepted.
                break;
            }

            queuedTeleports.poll();

            // Bedrock don't reply to teleport individually using a separate tick packet instead it just simply set its position to
            // the teleported position and then let us know the *next tick*, so we do the same!
            if (data instanceof RewindData rewind) {
                this.processRewind(player, rewind, packet);
            } else {
                this.processTeleport(player, data, packet);
            }
        }
    }

//    private void processDimensionSwitch(final BoarPlayer player, final TeleportCache.DimensionSwitch dimension, final PlayerAuthInputPacket packet) {
//        // Dimension switch should be followed with teleport so we don't have to do resync if the position mismatch.
//        if (packet.getPosition().distance(dimension.getPosition().toVector3f()) <= 1.0E-3F) {
//            player.setPos(new Vec3(packet.getPosition().sub(0, player.getYOffset(), 0)));
//            player.unvalidatedPosition = player.prevUnvalidatedPosition = player.position.clone();
//
//            player.velocity = Vec3.ZERO.clone();
//            player.predictionResult = new PredictionData(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
//        }
//    }

    private void processTeleport(final BoarPlayer player, final TeleportData data, final PlayerAuthInputPacket packet) {
        float distance = packet.getPosition().distance(data.getPosition().toVector3f());
        // I think I'm being a bit lenient but on Bedrock the position error seems to be a bit high.
        if (packet.getInputData().contains(PlayerAuthInputData.HANDLE_TELEPORT) && distance <= 1.0E-3F) {
            player.setPos(new Vec3(packet.getPosition().sub(0, player.getYOffset(), 0)));
            player.unvalidatedPosition = player.prevUnvalidatedPosition = player.position.clone();

            player.velocity = Vec3.ZERO.clone();
            player.predictionResult = new PredictionData(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO); // Yep!

            // This value can be true but since Geyser always send false then it is always false.
            player.onGround = false;
        } else {
            // Player rejected teleport OR this is not the latest teleport.
            if (!player.getTeleportUtil().isTeleporting()) {
                player.getTeleportUtil().teleport(data.getPosition());

                Boar.debug(player.getSession().name() + " rejected teleport with d=" + distance + ", resending teleport...", Boar.DebugMessage.INFO);
            }
        }
    }

    // Wouldn't it be nice to provide us a way to know when player accept rewind mojang :(
    // There will be edge cases where player lag right after accepting the stack id responsible for rewind... maaaaaaaybe
    // we could check for current offset and if it's close then player might possibility HAVEN'T received the rewind yet?
    // Just ignore the edge cases for now.
    private void processRewind(final BoarPlayer player, final RewindData rewind, final PlayerAuthInputPacket packet) {
        // The player still have other teleport that they need to accept, doing rewind here will be useless since the position will be replaced anyway.
        if (player.getTeleportUtil().isTeleporting()) {
            return;
        }

        if (player.isMovementExempted()) { // Fully exempted from rewind teleport.
            processExempted(player); // We just need to grab it from their actual position.
            return;
        }

        player.onGround = rewind.isOnGround();
        player.velocity = rewind.getTickEnd();
        player.setPos(rewind.getPosition().down(player.getYOffset()));
        player.prevUnvalidatedPosition = player.unvalidatedPosition = player.position.clone();

        player.getTeleportUtil().cacheRewindHistory(rewind.getTick(), rewind.getPosition());

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
        }
    }

    protected void processExempted(BoarPlayer player) {
        player.setPos(player.unvalidatedPosition);

        // Clear velocity out manually since we haven't handled em.
        player.certainVelocity = null;

        // This is fine, we only need tick end and use before and after to calculate ground.
        player.predictionResult = new PredictionData(Vec3.ZERO, player.velocity.y < 0 && player.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION) ? new Vec3(0, 1, 0) : Vec3.ZERO, player.unvalidatedTickEnd);
        player.velocity = player.unvalidatedTickEnd.clone();

        player.bestPossibility = Vector.NONE;
    }
}
