package ac.boar.anticheat.packets.input;

import ac.boar.anticheat.check.impl.reach.Reach;
import ac.boar.anticheat.check.impl.timer.Timer;
import ac.boar.anticheat.packets.input.legacy.LegacyAuthInputPackets;
import ac.boar.anticheat.packets.input.teleport.TeleportHandler;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.PredictionRunner;
import ac.boar.anticheat.teleport.data.RewindData;
import ac.boar.anticheat.teleport.data.TeleportData;
import ac.boar.anticheat.util.DimensionUtil;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.level.BedrockDimension;

public class AuthInputPackets extends TeleportHandler implements PacketListener {
    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        if (event.isCancelled()) {
            return;
        }

        final BoarPlayer player = event.getPlayer();
        if (!(event.getPacket() instanceof PlayerAuthInputPacket packet)) {
            return;
        }

        player.sinceLoadingScreen++;

        // -------------------------------------------------------------------------
        // Timer check start here.
        final long claimedTick = packet.getTick();
        if (claimedTick < 0 || claimedTick <= player.tick) { // Impossible, no way this can happen.
            player.kick("Impossible tick id=" + claimedTick);
            return;
        }

        // This is to prevent player skipping (more) ticks (than they're supposed to) after respawn to fuck up our rewind system.
        long distanceSincePrev = System.currentTimeMillis() - player.sinceAuthInput;
        if (distanceSincePrev < 50L) {
            player.tick++;
        } else {
            player.tick += Math.min(claimedTick - player.tick, distanceSincePrev / 30L);
        }

        player.sinceAuthInput = System.currentTimeMillis();

        if (player.tick != packet.getTick()) {
            player.kick("Invalid tick id, predicted=" + player.tick + ", actual=" + packet.getTick());
            return;
        }

        final Timer timer = (Timer) player.getCheckHolder().get(Timer.class);
        if (timer != null && timer.isInvalid()) {
            event.setCancelled(true);
            return;
        }

        // Timer check end here.
        // -------------------------------------------------------------------------

        if (player.serverBreakBlockValidator != null) {
            player.serverBreakBlockValidator.handle(packet);
        }

        LegacyAuthInputPackets.processAuthInput(player, packet, true);
        LegacyAuthInputPackets.updateUnvalidatedPosition(player, packet);

        ((Reach) player.getCheckHolder().get(Reach.class)).pollQueuedHits();

        player.tick();

        if (player.vehicleData != null) { // TODO: Vehicle prediction.
            player.position = player.unvalidatedPosition;
            return;
        }

        if (player.getSession().getPlayerEntity().getBedPosition() != null) {
            return;
        }

        player.insideUnloadedChunk = !player.compensatedWorld.isChunkLoaded((int) player.position.x, (int) player.position.z);
        // Don't try to predict player position in an unloaded chunk, it's not worth it and uh won't go well!
        // Just keep teleporting the player back until they loaded in, that way we shouldn't false post teleport... I think!
        // There isn't much room to abuse considering they're not loaded in any way... and the position is validated so
        // the player can't just send a position 100000 blocks out to avoid for eg: velocity.
        // TODO: Test properly uhhhh in some cases, I'm too lazy to care.
        if (player.insideUnloadedChunk) {
            player.getTeleportUtil().teleport(player.getTeleportUtil().getLastKnowValid());
        }

        if (player.getTeleportUtil().isTeleporting()) {
            this.processQueuedTeleports(player, packet);
        } else {
            if (player.isMovementExempted()) {
                processExempted(player);
            } else {
                if (!player.inLoadingScreen && player.sinceLoadingScreen >= 2 || player.unvalidatedTickEnd.lengthSquared() > 0) {
                    new PredictionRunner(player).run();
                } else {
                    player.velocity = Vec3.ZERO.clone();
                }
            }
        }

        LegacyAuthInputPackets.doPostPrediction(player, packet);
    }

    @Override
    public void onPacketSend(CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        if (event.getPacket() instanceof ChangeDimensionPacket packet) {
            int dimensionId = packet.getDimension();
            final BedrockDimension dimension = DimensionUtil.dimensionFromId(dimensionId);

            player.sendLatencyStack();
//            player.getTeleportUtil().queue(new TeleportCache.DimensionSwitch(new Vec3(packet.getPosition().up(EntityDefinitions.PLAYER.offset()))));
            player.getLatencyUtil().queue(() -> {
                if (player.compensatedWorld.getDimension() != dimension) {
                    player.currentLoadingScreen = packet.getLoadingScreenId();
                    player.inLoadingScreen = true;
                }

                player.compensatedWorld.getChunks().clear();
                player.compensatedWorld.setDimension(dimension);

                player.getFlagTracker().clear();
                player.getFlagTracker().flying(false);
            });
        }

        if (event.getPacket() instanceof MovePlayerPacket packet) {
            if (packet.getMode() == MovePlayerPacket.Mode.HEAD_ROTATION) {
                return;
            }

            if (player.runtimeEntityId != packet.getRuntimeEntityId()) {
                return;
            }

            // I think... there is some interpolation or some kind of smoothing when we use NORMAL?
            // Well it's a pain in the ass the support it, so just send teleport....
            if (packet.getMode() == MovePlayerPacket.Mode.NORMAL) {
                packet.setMode(MovePlayerPacket.Mode.TELEPORT);
            }

            player.getTeleportUtil().queue(new TeleportData(new Vec3(packet.getPosition())));
        }

        if (event.getPacket() instanceof CorrectPlayerMovePredictionPacket packet) {
            player.getTeleportUtil().queue(new RewindData(packet.getTick(), new Vec3(packet.getPosition()), new Vec3(packet.getDelta()), packet.isOnGround()));
        }
    }
}
