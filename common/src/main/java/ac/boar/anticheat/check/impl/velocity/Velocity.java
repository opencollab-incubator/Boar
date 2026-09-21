package ac.boar.anticheat.check.impl.velocity;

import ac.boar.anticheat.check.api.BaseCheck;
import ac.boar.anticheat.check.api.impl.OffsetHandlerCheck;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.teleport.TeleportUtil;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.api.anticheat.annotations.CheckInfo;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

@CheckInfo(name = "Velocity")
public final class Velocity extends BaseCheck implements OffsetHandlerCheck {

    private static final double BUFFER_LIMIT = 3;
    private static final double BUFFER_DECAY = 0.25;

    private double hBuffer;
    private double vBuffer;

    public Velocity(BoarPlayer player) {
        super(player);
    }

    public boolean canCheckPrediction() {
        final TeleportUtil teleports = player.getTeleportUtil();
        return player.bestPossibility.getType() == VectorType.VELOCITY
                && !player.isMovementExempted()
                && player.gameType != GameType.CREATIVE
                && player.gameType != GameType.SPECTATOR
                && player.getFlagTracker().has(EntityFlag.HAS_GRAVITY)
                && !player.getFlagTracker().has(EntityFlag.NO_AI)
                && !player.dead
                && player.vehicleData == null
                && !player.inLoadingScreen
                && player.sinceLoadingScreen >= 2
                && !player.insideUnloadedChunk
                && player.pendingDimensionSwitches == 0
                && player.tickSinceBlockResync <= 0
                && !player.thisTickSpinAttack && !player.dirtyRiptide
                && !player.penetratedLastFrame && !player.stuckInCollider
                && !teleports.isTeleporting() && !teleports.correctedWithin(2)
                && !teleports.hasPendingCorrection() && !teleports.isCorrectionCooldown();
    }

    public void capturePrediction() {
        if (!this.canCheckPrediction()) {
            return;
        }

        Vec3 took = player.unvalidatedPosition.subtract(player.prevUnvalidatedPosition);
        Vec3 pred = player.position.subtract(player.prevPosition);

        this.checkVertical(took.y, pred.y);
        this.checkHorizontal(took, pred);
    }

    private void checkVertical(float took, float predicted) {
        if (Math.abs(predicted) < 0.01) {
            return;
        }

        float pct = (took / predicted) * 100;
        if (pct < 99.9) {
            this.vBuffer = Math.min(this.vBuffer + 1, BUFFER_LIMIT);
            if (this.vBuffer >= BUFFER_LIMIT) {
                this.fail("Vertical", String.format("pct=%.3f kb=%.3f", pct, took));
            }
        } else {
            this.vBuffer = Math.max(this.vBuffer - BUFFER_DECAY, 0);
        }
    }

    private void checkHorizontal(Vec3 took, Vec3 predicted) {
        // check for either X or Z axis, whichever fails first (if none, decrease buffer)
        boolean checkedX = true, checkedZ = true;
        if (Math.abs(predicted.x) >= 0.003) {
            float pct = (took.x / predicted.x) * 100;
            if (pct < 99.9) {
                this.hBuffer = Math.min(this.hBuffer + 1, BUFFER_LIMIT);
                if (this.hBuffer >= BUFFER_LIMIT) {
                    this.fail("Horizontal", String.format("pct=%.3f kb=%.3f axis=X", pct, took.x));
                }
                return;
            }
        } else {
            checkedX = false;
        }

        if (Math.abs(predicted.z) >= 0.003) {
            float pct = (took.z / predicted.z) * 100;
            if (pct < 99.9) {
                this.hBuffer = Math.min(this.hBuffer + 1, BUFFER_LIMIT);
                if (this.hBuffer >= BUFFER_LIMIT) {
                    this.fail("Horizontal", String.format("pct=%.3f kb=%.3f axis=Z", pct, took.z));
                }
                return;
            }
        } else {
            checkedZ = false;
        }

        if (checkedX || checkedZ) {
            this.hBuffer = Math.max(this.hBuffer - BUFFER_DECAY, 0);
        }
    }
}
