package ac.boar.anticheat.packets.input;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.DimensionSwitchAck;
import ac.boar.anticheat.ack.types.RespawnStateAck;
import ac.boar.anticheat.check.impl.reach.Reach;
import ac.boar.anticheat.check.impl.badpackets.BadPacketA;
import ac.boar.anticheat.check.impl.timer.Timer;
import ac.boar.anticheat.packets.input.legacy.LegacyAuthInputPackets;
import ac.boar.anticheat.packets.input.teleport.TeleportHandler;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.PredictionRunner;
import ac.boar.anticheat.teleport.data.TeleportData;
import ac.boar.anticheat.util.Dimension;
import ac.boar.anticheat.util.DimensionUtil;
import ac.boar.anticheat.util.geyser.BoarChunk;
import ac.boar.anticheat.util.geyser.BoarChunkSection;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;

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

        if (claimedTick < 0) { // Impossible, no way this can happen.
            if (player.disableMitigations()) {
                player.getCheckHolder().manuallyFail(BadPacketA.class, "impossible tick=" + claimedTick);
            } else {
                player.kick("Impossible tick id=" + claimedTick);
            }
            return;
        }

        player.tick = claimedTick;
        player.sinceAuthInput = System.currentTimeMillis();

        final Timer timer = (Timer) player.getCheckHolder().get(Timer.class);
        final Reach reach = (Reach) player.getCheckHolder().get(Reach.class);
        if (timer != null && timer.isInvalid()) {
            if (!player.disableMitigations()) {
                event.setCancelled(true);
                if (reach != null) {
                    reach.invalidatePending();
                }
                Boar.debug(player.getSession().name() + ": [movement-debug] cancelled auth-input reason=timer tick=" + player.tick + " packetTick=" + packet.getTick() + " pos=" + packet.getPosition() + " delta=" + packet.getDelta(), Boar.DebugMessage.WARNING);
                return;
            }
        }

        // Timer check end here.
        // -------------------------------------------------------------------------

        if (player.serverBreakBlockValidator != null) {
            player.serverBreakBlockValidator.handle(packet);
        }

        LegacyAuthInputPackets.processAuthInput(player, packet, true);
        LegacyAuthInputPackets.updateUnvalidatedPosition(player, packet);

        final int chunkX = GenericMath.floor(player.position.x) >> 4;
        final int chunkZ = GenericMath.floor(player.position.z) >> 4;
        final BoarChunk chunkInside = player.compensatedWorld.getChunk(chunkX, chunkZ);
        if (chunkInside != null) {
            player.insideUnloadedChunk = false;
            if (Boar.DEBUG_CHUNKS && !chunkInside.hasAllSections() && chunkInside.warnForMissingSections()) {
                StringBuilder missing = null;
                for (int idx = 0; idx < chunkInside.sections().length; idx++) {
                    BoarChunkSection sec = chunkInside.getSection(idx);
                    if (sec == null) {
                        if (missing == null) missing = new StringBuilder("["); else missing.append(", ");
                        missing.append(idx);
                    }
                }
                if (missing != null) missing.append("]"); else missing = new StringBuilder("[]");
                Boar.chunkDebug(player.getSession().name() + ": inside loaded chunk but has missing sub-chunks: " + missing, Boar.DebugMessage.WARNING);
            }
        } else {
            player.insideUnloadedChunk = true;
        }


        // Start a fresh movement trace for this tick, with a snapshot of the start state.
        player.getMovementTrace().begin();

        if (reach != null) { // null when the Reach check is disabled via disabled-checks - don't NPE.
            reach.validatePending();
        }

        player.tick();

        if (player.vehicleData != null) { // TODO: Vehicle prediction.
            player.getMovementTrace().log("path: vehicle, accepted client position");
            player.position = player.unvalidatedPosition;
            player.compensatedWorld.cleanChunksAtPlayerPosition();
            return;
        }

        if (player.getEntity().bedPosition() != null) {
            player.getMovementTrace().log("path: in bed, skipped");
            return;
        }

        if (player.getTeleportUtil().isTeleporting()) {
            player.getMovementTrace().log("path: teleporting, processing queued teleports");
            this.processQueuedTeleports(player, packet);
        } else if (player.dead && player.certainVelocity == null) {
            // From vanilla client - Player::isImmobile is true at 0 health unless the knocked-back-on-death flag is set, and the
            // client reports a fixed position with a zero delta
            player.getMovementTrace().log("path: dead, no movement expected");
            processImmobile(player);
        } else if (player.insideUnloadedChunk) {
            player.getMovementTrace().log("path: unloaded chunk, no movement expected");
            processImmobile(player);
        } else {
            if (player.isMovementExempted()
                    || player.inLoadingScreen
                    || player.sinceLoadingScreen < 2
                    || player.tickSinceBlockResync > 0) {
                player.getMovementTrace().log("path: exempted (movementExempt=" + player.isMovementExempted()
                        + " inLoadingScreen=" + player.inLoadingScreen
                        + " sinceLoadingScreen=" + player.sinceLoadingScreen
                        + " blockResync=" + player.tickSinceBlockResync + ")");
                processExempted(player);
            } else {
                player.getMovementTrace().log("path: prediction");
                new PredictionRunner(player).run();
            }
        }

        player.insideUnloadedChunk = !player.compensatedWorld.isChunkLoadedAt(player.position.x, player.position.z);
        player.compensatedWorld.cleanChunksAtPlayerPosition();

        LegacyAuthInputPackets.doPostPrediction(player, packet);
    }

    @Override
    public void onPacketSend(CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        if (event.getPacket() instanceof ChangeDimensionPacket packet) {
            int dimensionId = packet.getDimension();
            final Dimension dimension = DimensionUtil.dimensionFromId(dimensionId);

            player.pendingDimensionSwitches++;
            player.queueAcknowledgment(new DimensionSwitchAck(dimension, packet.getLoadingScreenId()));
        }

        if (event.getPacket() instanceof MovePlayerPacket packet
                && packet.getRuntimeEntityId() == player.runtimeEntityId
                && packet.getMode() != MovePlayerPacket.Mode.HEAD_ROTATION) {
            // Convert unsupported smoothed and respawn movement modes to teleports.
            if (packet.getMode() == MovePlayerPacket.Mode.NORMAL || packet.getMode() == MovePlayerPacket.Mode.RESPAWN) {
                packet.setMode(MovePlayerPacket.Mode.TELEPORT);
            }

            packet.setOnGround(true);
            Boar.debug(player.getSession().name() + ": [movement-debug] queued server teleport source=MovePlayerPacket mode="
                    + packet.getMode() + " cause=" + packet.getTeleportationCause() + " pos=" + packet.getPosition()
                    + " tick=" + player.tick + " dead=" + player.dead, Boar.DebugMessage.WARNING);
            player.getTeleportUtil().queue(new TeleportData(new Vec3(packet.getPosition()), packet.isOnGround()));
        }

        // The vanilla server sends runtime id 0 in the player's own RespawnPacket (Player::recheckSpawnPosition) so accept 0 as well as the player's id.
        if (event.getPacket() instanceof RespawnPacket packet &&
                (packet.getRuntimeEntityId() == player.runtimeEntityId || packet.getRuntimeEntityId() == 0) &&
                packet.getState() != RespawnPacket.State.CLIENT_READY) {
            player.sendLatencyStack(new RespawnStateAck(packet.getState()));

            if (packet.getState() == RespawnPacket.State.SERVER_READY) {
                Boar.debug(player.getSession().name() + ": [movement-debug] queued server teleport source=RespawnPacket runtimeId="
                        + packet.getRuntimeEntityId() + " pos=" + packet.getPosition() + " tick=" + player.tick, Boar.DebugMessage.WARNING);
                player.getTeleportUtil().queue(new TeleportData(new Vec3(packet.getPosition()), true));
            }
        }
    }
}
