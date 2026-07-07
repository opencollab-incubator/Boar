package ac.boar.anticheat.teleport;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.MovementCorrectionAck;
import ac.boar.anticheat.ack.types.TeleportAcceptAck;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.branch.BranchTracker;
import ac.boar.anticheat.teleport.data.TeleportCache;
import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;

import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@RequiredArgsConstructor
@Getter
public class TeleportUtil {
    private final BoarPlayer player;

    private Vector3f lastKnownValid = Vector3f.ZERO;
    private int pendingCorrections;
    @Setter
    private boolean correctionCooldown;

    // Normal teleport part.
    private final Queue<TeleportCache> queuedTeleports = new ConcurrentLinkedQueue<>();

    public boolean isTeleporting() {
        return !this.queuedTeleports.isEmpty();
    }

    public void teleportTo(final Vector3f position) {
        this.teleportTo(new TeleportCache.Normal(new Vec3(position), false));
    }

    public void teleportTo(final TeleportCache cache) {
        if (this.isTeleporting()) {
            Boar.debug("[movement-debug] skipped teleport reason=already-teleporting queued=" + this.queuedTeleports.size(), Boar.DebugMessage.WARNING);
            return;
        }

        final TeleportCache.Normal teleport = (TeleportCache.Normal) cache;
        final MovePlayerPacket packet = new MovePlayerPacket();
        packet.setRuntimeEntityId(player.runtimeEntityId);
        packet.setPosition(teleport.getPosition().toVector3f());
        packet.setRotation(player.rotation);
        packet.setOnGround(teleport.isOnGround());
        packet.setMode(MovePlayerPacket.Mode.TELEPORT);
        packet.setTeleportationCause(MovePlayerPacket.TeleportationCause.BEHAVIOR);

        this.player.getBranchTracker().discardBranches("teleport-sent");
        this.player.getConnection().sendPacket(packet);
        Boar.debug("[movement-debug] sent teleport pos=" + teleport.getPosition() + " lastKnown=" + this.lastKnownValid, Boar.DebugMessage.WARNING);
    }

    public void queueTeleport(final Vec3 position, final boolean onGround) {
        queue(new TeleportCache.Normal(position, onGround));
        this.lastKnownValid = position.toVector3f();
    }

    public void queue(TeleportCache cache) {
        this.player.getBranchTracker().discardBranches("teleport-queued");
        this.queuedTeleports.add(cache);
        player.sendLatencyStack(new TeleportAcceptAck(cache));
    }

    public boolean hasPendingCorrection() {
        return this.pendingCorrections > 0;
    }

    public void addPendingCorrection() {
        this.pendingCorrections++;
    }

    public void removePendingCorrection() {
        if (this.pendingCorrections > 0) {
            this.pendingCorrections--;
        }
    }

    public void updateLastKnownValid(Vector3f position) {
        this.lastKnownValid = position;
    }

    public void correct() {
        if (this.isTeleporting()) {
            Boar.debug("[movement-debug] skipped correction reason=already-teleporting queued=" + this.queuedTeleports.size() + " tick=" + player.tick, Boar.DebugMessage.WARNING);
            return;
        }

        if (this.hasPendingCorrection()) {
            Boar.debug("[movement-debug] skipped correction reason=already-correcting pending=" + this.pendingCorrections + " tick=" + player.tick, Boar.DebugMessage.WARNING);
            return;
        }

        if (player.isMovementExempted()) {
            Boar.debug("[movement-debug] skipped correction reason=movement-exempt tick=" + player.tick + " " + player.movementExemptReason(), Boar.DebugMessage.WARNING);
            return;
        }

        final CorrectPlayerMovePredictionPacket packet = new CorrectPlayerMovePredictionPacket();
        packet.setPosition(player.position.add(0, player.getYOffset(), 0).toVector3f());
        packet.setOnGround(player.onGround);
        packet.setTick(player.tick);
        packet.setDelta(player.velocity.toVector3f());
        packet.setVehicleRotation(Vector2f.ZERO);
        packet.setPredictionType(player.vehicleData != null ? PredictionType.VEHICLE : PredictionType.PLAYER);

        this.addPendingCorrection();
        this.correctionCooldown = true;
        this.player.sendLatencyStack(new MovementCorrectionAck());
        this.player.getConnection().sendPacketImmediately(packet);
        this.player.getBranchTracker().discardBranches("movement-correction");
        this.player.getBranchTracker().enterDormancy(BranchTracker.Dormancy.SPRINT);
        Boar.debug("[movement-debug] sent correction tick=" + player.tick + " pos=" + packet.getPosition() + " delta=" + packet.getDelta() + " onGround=" + player.onGround, Boar.DebugMessage.WARNING);

        final Vec3 diff = player.position.subtract(player.unvalidatedPosition);
        this.player.getSession().sendMessage("§cCORRECTION! posDiff=" + String.format(Locale.ROOT, "[%.4f, %.4f, %.4f] (total=%.5f)", diff.getX(), diff.getY(), diff.getZ(), diff.length()) + " simTick=" + player.tick);
    }
}
