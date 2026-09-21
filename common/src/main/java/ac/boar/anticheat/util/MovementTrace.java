package ac.boar.anticheat.util;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.data.vanilla.Attribute;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.data.vanilla.StatusEffect;
import ac.boar.anticheat.player.BoarPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Records what the movement prediction does on each tick.
 *
 * <p>The trace collects log lines into a buffer for the current tick. When a new tick starts,
 * the old buffer moves into a small history. The history keeps the last {@link #HISTORY_TICKS}
 * ticks. Nothing is written to the log during normal play.
 *
 * <p>When the prediction fails hard enough to send a correction, {@link #dump(String)} builds
 * one text block with the full history and the failing tick, and {@link #flush(String)} writes
 * that block to the log. This gives the data needed to re-create the failure: the client input,
 * the start state, and each step of the simulation.
 */
public final class MovementTrace {
    private static final int HISTORY_TICKS = 10;

    private final BoarPlayer player;
    private final ArrayDeque<List<String>> history = new ArrayDeque<>(HISTORY_TICKS);
    private List<String> current = new ArrayList<>();

    public MovementTrace(final BoarPlayer player) {
        this.player = player;
    }

    /**
     * Starts a new tick. Moves the lines of the previous tick into the history,
     * then records a snapshot of the state the prediction starts from.
     */
    public void begin() {
        if (!this.current.isEmpty()) {
            if (this.history.size() >= HISTORY_TICKS) {
                this.history.removeFirst();
            }
            this.history.addLast(this.current);
            this.current = new ArrayList<>();
        }

        this.snapshot();
    }

    /** Adds one line to the buffer of the current tick. */
    public void log(final String line) {
        this.current.add(line);
    }

    /**
     * Builds the full trace text: the reason, the retained history, and the current tick.
     * Clears the history afterwards. The current tick keeps collecting lines until the next
     * {@link #begin()} call, so steps after the failure still get recorded.
     */
    public String dump(final String reason) {
        final StringBuilder out = new StringBuilder(4096);
        out.append(reason);

        int age = this.history.size();
        for (final List<String> tickLines : this.history) {
            out.append("\n--- tick history (-").append(age--).append(") ---");
            for (final String line : tickLines) {
                out.append("\n  ").append(line);
            }
        }

        out.append("\n--- failing tick ---");
        for (final String line : this.current) {
            out.append("\n  ").append(line);
        }

        this.history.clear();
        return out.toString();
    }

    /** Writes the trace to the log as one message. */
    public void flush(final String reason) {
        // Write without the debug-mode gate. A flush only happens on a failure, and that is
        // exactly the data we need to re-create the problem.
        Boar.getInstance().getPlatform().logger().warn(
                "[movement-trace] " + player.getSession().name() + ": " + this.dump(reason));
    }

    /** Records the state of the player at the start of the tick. */
    private void snapshot() {
        log("tick=" + player.tick);
        log("client: pos=" + player.unvalidatedPosition + " prevPos=" + player.prevUnvalidatedPosition
                + " claimedDelta=" + player.unvalidatedTickEnd + " analogMotion=" + player.clientMotion
                + " yaw=" + player.yaw + " pitch=" + player.pitch);
        log("server: pos=" + player.position + " prevPos=" + player.prevPosition
                + " nativeOriginY=" + player.nativeOriginY + " claimedNativeOriginY=" + player.unvalidatedNativeOriginY
                + " vel=" + player.velocity + " lastTickFinalVel=" + player.lastTickFinalVelocity
                + " certainVel=" + (player.certainVelocity == null ? "none"
                        : player.certainVelocity.getType() + ":" + player.certainVelocity.getVelocity()));
        log("state: onGround=" + player.onGround + " hColl=" + player.horizontalCollision
                + " vColl=" + player.verticalCollision + " touchingWater=" + player.touchingWater
                + " fluidHeights=" + player.fluidHeight + " soulSand=" + player.soulSandBelow
                + " stuckMul=" + player.stuckSpeedMultiplier + " stuckInCollider=" + player.stuckInCollider
                + " fallDistance=" + player.fallDistance);
        log("input: vec=" + player.input + " data=" + player.getInputData()
                + " inputMode=" + player.inputMode + " gameType=" + player.gameType);
        log("flags: " + player.getFlagTracker().cloneFlags() + " flying=" + player.getFlagTracker().isFlying()
                + " wasFlying=" + player.getFlagTracker().isWasFlying() + " abilities=" + player.abilities);
        log("effects: " + this.effectsString() + " speedAttr=" + this.speedString());
        log("counters: glideBoost=" + player.glideBoostTicks + " swimAmount=" + player.swimAmount
                + " sinceCrawl=" + player.ticksSinceCrawling + " sinceCanSlowdown=" + player.ticksSinceCanSlowdown
                + " autoSpin=" + player.autoSpinAttackTicks + " sinceLoadingScreen=" + player.sinceLoadingScreen
                + " blockResync=" + player.tickSinceBlockResync);
        log("world: teleporting=" + player.getTeleportUtil().isTeleporting()
                + " pendingCorrection=" + player.getTeleportUtil().hasPendingCorrection()
                + " correctionCooldown=" + player.getTeleportUtil().isCorrectionCooldown()
                + " unloadedChunk=" + player.insideUnloadedChunk + " inLoadingScreen=" + player.inLoadingScreen
                + " dead=" + player.dead
                + " vehicle=" + (player.vehicleData != null) + " box=[" + player.boundingBox.minX + ","
                + player.boundingBox.minY + "," + player.boundingBox.minZ + " -> " + player.boundingBox.maxX
                + "," + player.boundingBox.maxY + "," + player.boundingBox.maxZ + "]");
    }

    private String effectsString() {
        final Map<Effect, StatusEffect> effects = player.getActiveEffects();
        if (effects.isEmpty()) {
            return "none";
        }

        final StringBuilder out = new StringBuilder();
        for (final Map.Entry<Effect, StatusEffect> entry : effects.entrySet()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(entry.getKey()).append("(amp=").append(entry.getValue().getAmplifier())
                    .append(", dur=").append(entry.getValue().getDuration()).append(")");
        }
        return out.toString();
    }

    private String speedString() {
        // The movement attribute can be absent right after login. Do not crash the snapshot for it.
        final AttributeInstance movement = player.attributes.get(Attribute.MOVEMENT.getIdentifier());
        return movement == null ? "unknown" : String.valueOf(player.getSpeed());
    }
}
