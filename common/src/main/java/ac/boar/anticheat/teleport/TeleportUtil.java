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

    private TeleportData acceptedTeleport;
    private boolean skipTravel;
    private boolean applyTeleportWaterInput;
    @Getter
    private Vec3 pendingTeleportPosition;
    @Getter
    private int pendingTeleports;
    private long generation;
    @Getter
    private Vec3 interpolationTarget;
    @Getter
    private int interpolationTicks;

    public void teleport(final Vec3 vec3) {
        if (this.isTeleporting()) {
            Boar.debug(player.getSession().name() + ": [movement-debug] skipped teleport reason=already-teleporting pending=" + this.pendingTeleports, Boar.DebugMessage.WARNING);
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
        this.pendingTeleports++;
        this.pendingTeleportPosition = data.getPosition().clone();
        player.sendLatencyStack(new TeleportAcceptAck(data, this.generation));
    }

    public void accept(TeleportData data, long generation) {
        if (generation != this.generation) {
            return;
        }
        if (this.pendingTeleports > 0) {
            this.pendingTeleports--;
        }
        this.acceptedTeleport = data;
        player.onGround = data.isOnGround();
        if (data.getSource() == TeleportData.Source.MOVE_PLAYER_NORMAL) {
            final Vec3 origin = new Vec3(player.position.x, player.nativeOriginY, player.position.z);
            player.velocity = data.getPosition().subtract(origin);
            player.certainVelocity = null;
            // Player::onMovePlayerPacketNormal only does interpolation if the destination is loaded
            if (player.compensatedWorld.isChunkLoadedAt(data.getPosition().x, data.getPosition().z)) {
                this.startInterpolation(data.getPosition());
            } else {
                this.setPacketPosition(data.getPosition());
            }
        } else {
            // Actor::teleportTo and Player::resetUserPos clear velocity and interpolation.
            this.clearInterpolation();
            this.setPacketPosition(data.getPosition());
            player.velocity = Vec3.ZERO.clone();
            player.certainVelocity = null;
            if (data.getSource() != TeleportData.Source.MOVE_PLAYER_RESPAWN) {
                player.fallDistance = 0;
                // A later NORMAL or RESPAWN packet does not remove HasTeleportedFlagComponent.
                this.skipTravel = true;
                this.applyTeleportWaterInput |= data.getSource() == TeleportData.Source.MOVE_PLAYER_TELEPORT;
            }
        }
    }

    public TeleportData takeAcceptedTeleport() {
        final TeleportData data = this.acceptedTeleport;
        this.acceptedTeleport = null;
        return data;
    }

    public boolean takeSkipTravel() {
        final boolean skip = this.skipTravel;
        this.skipTravel = false;
        return skip;
    }

    public boolean takeApplyTeleportWaterInput() {
        final boolean apply = this.applyTeleportWaterInput;
        this.applyTeleportWaterInput = false;
        return apply;
    }

    public void setPacketPosition(Vec3 position) {
        player.setPos(position.down(player.getYOffset()));
        player.nativeOriginY = position.y;
        player.insideUnloadedChunk = !player.compensatedWorld.isChunkLoadedAt(position.x, position.z);
    }

    public void startInterpolation(Vec3 target) {
        this.interpolationTarget = target.clone();
        this.interpolationTicks = 3;
    }

    public void tickInterpolation() {
        if (this.interpolationTicks > 0) {
            this.interpolationTicks--;
        }
    }

    public void clearInterpolation() {
        this.interpolationTarget = null;
        this.interpolationTicks = 0;
    }

    public void clearTeleports() {
        // Ignore acknowledgments that arrive after a reset or vehicle change.
        this.generation++;
        this.acceptedTeleport = null;
        this.skipTravel = false;
        this.applyTeleportWaterInput = false;
        this.pendingTeleports = 0;
        this.pendingTeleportPosition = null;
        this.clearInterpolation();
    }

    /**
     * Resets all teleport state to a position that the server already established.
     */
    public void reset(Vec3 position) {
        this.clearTeleports();
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
        return this.pendingTeleports > 0 || this.acceptedTeleport != null || this.interpolationTicks > 0;
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
            Boar.debug(player.getSession().name() + ": [movement-debug] skipped correction reason=already-teleporting pending=" + this.pendingTeleports + " tick=" + player.tick, Boar.DebugMessage.WARNING);
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
        //this.player.getSession().sendMessage("Correction sent: sim tick " + correction.getTick());
        Boar.debug(player.getSession().name() + ": [movement-debug] sent correction tick=" + player.tick + " pos=" + correction.getPosition() + " delta=" + correction.getDelta() + " onGround=" + player.onGround, Boar.DebugMessage.WARNING);
    }
}
