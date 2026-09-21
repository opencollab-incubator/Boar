package ac.boar.anticheat.teleport;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.types.MovementCorrectionAck;
import ac.boar.anticheat.ack.types.TeleportAcceptAck;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.teleport.data.TeleportData;
import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.protocol.bedrock.data.PredictionType;
import org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@RequiredArgsConstructor
public class TeleportUtil {
    private final BoarPlayer player;

    @Getter
    private Vec3 lastKnownValid = Vec3.ZERO;
    private int pendingCorrections;
    @Getter
    private boolean correctionCooldown;

    @Getter
    private long lastCorrectionTick = -1;

    @Getter
    private final Queue<TeleportData> queuedTeleports = new ConcurrentLinkedQueue<>();

    public void teleport(final Vec3 vec3) {
        if (this.isTeleporting()) {
            Boar.debug(player.getSession().name() + ": [movement-debug] skipped teleport reason=already-teleporting queued=" + this.queuedTeleports.size(), Boar.DebugMessage.WARNING);
            return;
        }

        final MovePlayerPacket packet = new MovePlayerPacket();
        packet.setRuntimeEntityId(player.runtimeEntityId);
        packet.setPosition(vec3.toVector3f());
        packet.setRotation(player.rotation);
        packet.setOnGround(false);
        packet.setMode(MovePlayerPacket.Mode.TELEPORT);
        packet.setTeleportationCause(MovePlayerPacket.TeleportationCause.BEHAVIOR);

        this.player.getConnection().sendPacket(packet);
        Boar.debug(player.getSession().name() + ": [movement-debug] sent teleport pos=" + vec3 + " lastKnown=" + this.lastKnownValid, Boar.DebugMessage.WARNING);
    }

    public void queue(TeleportData data) {
        this.queuedTeleports.add(data);
        player.sendLatencyStack(new TeleportAcceptAck(data));
    }

    /**
     * Resets all teleport state to a position that the server already established.
     */
    public void reset(Vec3 position) {
        this.queuedTeleports.clear();
        this.lastKnownValid = position.clone();
        this.pendingCorrections = 0;
        this.correctionCooldown = false;
        this.lastCorrectionTick = -1;
    }

    public void markCorrected() {
        this.lastCorrectionTick = player.tick;
    }

    public boolean correctedWithin(int ticks) {
        return this.lastCorrectionTick >= 0 && (player.tick - this.lastCorrectionTick) <= ticks;
    }

    public boolean isTeleporting() {
        return !this.queuedTeleports.isEmpty();
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

    public void updateLastKnownValid(Vec3 position) {
        this.lastKnownValid = position.clone();
    }

    public void setCorrectionCooldown(boolean correctionCooldown) {
        this.correctionCooldown = correctionCooldown;
    }

    public void correct() {
        if (player.disableMitigations()) {
            return;
        }

        if (this.isTeleporting()) {
            Boar.debug(player.getSession().name() + ": [movement-debug] skipped correction reason=already-teleporting queued=" + this.queuedTeleports.size() + " tick=" + player.tick, Boar.DebugMessage.WARNING);
            return;
        }

        if (this.hasPendingCorrection()) {
            Boar.debug(player.getSession().name() + ": [movement-debug] skipped correction reason=already-correcting pending=" + this.pendingCorrections + " tick=" + player.tick, Boar.DebugMessage.WARNING);
            return;
        }

        if (player.isMovementExempted()) {
            Boar.debug(player.getSession().name() + ": [movement-debug] skipped correction reason=movement-exempt tick=" + player.tick, Boar.DebugMessage.WARNING);
            return;
        }

        final CorrectPlayerMovePredictionPacket correction = new CorrectPlayerMovePredictionPacket();
        correction.setPosition(player.position.add(0, player.getYOffset() + 0.001f, 0).toVector3f());
        correction.setOnGround(player.onGround);
        correction.setTick(player.tick);
        correction.setDelta(player.velocity.toVector3f());
        correction.setVehicleRotation(Vector2f.ZERO);
        correction.setPredictionType(player.vehicleData != null ? PredictionType.VEHICLE : PredictionType.PLAYER);

        this.addPendingCorrection();
        this.correctionCooldown = true;
        this.player.sendLatencyStack(new MovementCorrectionAck());
        this.player.getConnection().sendPacket(correction);
        this.player.getSession().sendMessage("Correction sent: sim tick " + correction.getTick());
        Boar.debug(player.getSession().name() + ": [movement-debug] sent correction tick=" + player.tick + " pos=" + correction.getPosition() + " delta=" + correction.getDelta() + " onGround=" + player.onGround, Boar.DebugMessage.WARNING);
    }
}
