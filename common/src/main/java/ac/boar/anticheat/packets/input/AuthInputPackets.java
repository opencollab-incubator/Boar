package ac.boar.anticheat.packets.input;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.DimensionSwitchAck;
import ac.boar.anticheat.check.impl.reach.Reach;
import ac.boar.anticheat.check.impl.timer.Timer;
import ac.boar.anticheat.data.input.PredictionResult;
import ac.boar.anticheat.packets.input.legacy.LegacyAuthInputPackets;
import ac.boar.anticheat.packets.input.teleport.TeleportHandler;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.PredictionRunner;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.teleport.data.TeleportCache;
import ac.boar.anticheat.util.Dimension;
import ac.boar.anticheat.util.DimensionUtil;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.entity.EntityDefinition;
import ac.boar.mappings.entity.EntityDefinitions;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

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
            player.kick("Impossible tick id=" + claimedTick);
            return;
        }

        player.tick = claimedTick;
        player.sinceAuthInput = System.currentTimeMillis();

        final Timer timer = (Timer) player.getCheckHolder().get(Timer.class);
        if (timer != null && timer.isInvalid()) {
            event.setCancelled(true);
            Boar.debug("[movement-debug] cancelled auth-input reason=timer tick=" + player.tick + " packetTick=" + packet.getTick() + " pos=" + packet.getPosition() + " delta=" + packet.getDelta(), Boar.DebugMessage.WARNING);
            return;
        }

        // Timer check end here.
        // -------------------------------------------------------------------------

        if (player.serverBreakBlockValidator != null) {
            player.serverBreakBlockValidator.handle(packet);
        }

        LegacyAuthInputPackets.processAuthInput(player, packet, true);
        LegacyAuthInputPackets.updateUnvalidatedPosition(player, packet);

        player.tick();
        ((Reach) player.getCheckHolder().get(Reach.class)).validatePending();

        if (player.vehicleData != null) { // TODO: Vehicle prediction.
            player.position = player.unvalidatedPosition;
            player.getBranchTracker().discardBranches("in-vehicle");
            player.pendingServerMovementSpeed = null;
            return;
        }

        if (player.getEntity().bedPosition() != null) {
            player.getBranchTracker().discardBranches("in-bed");
            player.pendingServerMovementSpeed = null;
            return;
        }

        boolean movementExempt = player.isMovementExempted();
        if (movementExempt != player.loggedMovementExempt) {
            Boar.debug("[fly-debug] movement-exempt " + (movementExempt ? "ENTER" : "EXIT") + " tick=" + player.tick + " " + player.movementExemptReason(), Boar.DebugMessage.INFO);
            player.loggedMovementExempt = movementExempt;
        }

        if (movementExempt) {
            player.setPos(player.unvalidatedPosition);

            // Clear velocity out manually since we haven't handled em.
            player.certainVelocity = null;
            player.getBranchTracker().discardBranches("movement-exempt");

            // This is fine, we only need tick end and use before and after to calculate ground.
            player.predictionResult = new PredictionResult(Vec3.ZERO, player.velocity.y < 0 && player.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION) ? new Vec3(0, 1, 0) : Vec3.ZERO, player.unvalidatedTickEnd);
            player.velocity = player.unvalidatedTickEnd.clone();

            player.bestPossibility = Vector.NONE;
        } else {
            if (!player.inLoadingScreen && player.sinceLoadingScreen >= 2 || player.unvalidatedTickEnd.lengthSquared() > 0) {
                new PredictionRunner(player).run();
            } else {
                player.velocity = Vec3.ZERO.clone();
            }
        }

        player.insideUnloadedChunk = !player.compensatedWorld.isChunkLoaded((int) player.position.x, (int) player.position.z);
        // Don't try to predict player position in an unloaded chunk, it's not worth it and uh won't go well!
        // Just keep teleporting the player back until they loaded in, that way we shouldn't false post teleport... I think!
        // There isn't much room to abuse considering they're not loaded in any way... and the position is validated so
        // the player can't just send a position 100000 blocks out to avoid for eg: velocity.

        // TODO: Test properly uhhhh in some cases, I'm too lazy to care.
        if (player.insideUnloadedChunk) {
            player.getBranchTracker().discardBranches("unloaded-chunk");
            player.getTeleportUtil().teleportTo(player.getTeleportUtil().getLastKnownValid());
        }

        this.processQueuedTeleports(player, packet);
        LegacyAuthInputPackets.doPostPrediction(player, packet);
    }

    @Override
    public void onPacketSend(CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        if (event.getPacket() instanceof ChangeDimensionPacket packet) {
            int dimensionId = packet.getDimension();
            final Dimension dimension = DimensionUtil.dimensionFromId(dimensionId);

            player.getTeleportUtil().queue(new TeleportCache.DimensionSwitch(new Vec3(packet.getPosition().up(EntityDefinitions.PLAYER.find().map(EntityDefinition::offset).orElse(1.62f)))));
            player.queueAcknowledgment(new DimensionSwitchAck(dimension, packet.getLoadingScreenId()));
        }

        if (event.getPacket() instanceof MovePlayerPacket packet
                && packet.getRuntimeEntityId() == player.runtimeEntityId
                && packet.getMode() != MovePlayerPacket.Mode.HEAD_ROTATION) {
            /*
             * TODO: We should be able to support smoothed-teleports for servers that want to use it in the future.
             * For now, just setting these to normal teleports should help us not break anything else.
             * Also, FUCK the RESPAWN/RESET mode, it behaves differently from teleport and messes up the simulation so
             * we'll just convert that to a normal teleport as well. What could possibly go wrong?
             */
            if (packet.getMode() == MovePlayerPacket.Mode.NORMAL || packet.getMode() == MovePlayerPacket.Mode.RESPAWN) {
                packet.setMode(MovePlayerPacket.Mode.TELEPORT);
            }
            player.getTeleportUtil().queueTeleport(new Vec3(packet.getPosition()), packet.isOnGround());
        }
    }
}
