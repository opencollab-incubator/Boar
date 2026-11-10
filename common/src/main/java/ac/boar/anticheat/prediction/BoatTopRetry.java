package ac.boar.anticheat.prediction;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.compensated.world.CompensatedWorldImpl;
import ac.boar.anticheat.player.data.VehicleType;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.util.math.Box;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;

/**
 * Retry for a boat exit. The client's player lands on the boat it just left and ours does not, or the other way round,
 * or both stand on it at different heights.
 * Why: after the ride ends the client shows the boat with its interpolator, so where the boat top is depends on the client's clock.
 * Model: the boat top may be anywhere in the set the client can show at the start of the tick (FollowerEnvelope.renderSet, built only
 * from times Boar saw). We run the tick again with that boat's collision moved. The run is kept only if it ends at the claimed position
 * and velocity (normal acceptance) with the claimed vertical collision. Otherwise, nothing changes.
 * The set converges to a point once the boat rests, so no time window is needed.
 * - The client claims a vertical collision and its feet are inside the set: try the top at the claimed feet.
 *   This also covers both players standing on the boat at different heights.
 * - We had a vertical collision and the client claims none: try the lowest top in the set.
 * In-game testing showed corrections on deep water exits without this retry. With it, the retries were kept.
 * Not covered: a boat this player did not drive, a knockback tick, a boat whose envelope is not trusted yet (no ACK answered),
 * and a boat whose follower history did not tick this tick or whose clock is not trusted (FollowerHistory.untrustedClockReason;
 * CompensatedWorldImpl.trustedBoatCollisionBox then gives no box).
 */
public final class BoatTopRetry {
    private BoatTopRetry() {
    }

    public static void attempt(final BoarPlayer player, final PredictionState start) {
        if (player.vehicleData != null || player.disableMitigations() || player.bestPossibility.getType() == VectorType.VELOCITY) {
            return;
        }
        final boolean claimedVertical = player.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION);
        if (!claimedVertical && !player.verticalCollision) {
            return;
        }
        final float acceptance = player.getPosAcceptanceLimit();
        // Also when only the velocity is off: a different landing can still end at a matching position, and the gap shows one tick later.
        // In-game testing showed this in deep water.
        if (CollisionRetry.offset(player) < acceptance && player.velocity.distanceTo(player.unvalidatedTickEnd) < acceptance) {
            return;
        }
        if (!(player.compensatedWorld instanceof CompensatedWorldImpl world)) {
            return;
        }

        final Box claimed = player.dimensions.getBoxAt(player.unvalidatedPosition);
        // A failed try puts this state back. Restoring clones it (PredictionState.restore), so one capture serves every boat.
        PredictionState first = null;
        for (final EntityCache boat : world.getEntities().values()) {
            if (boat.getVehicleType() != VehicleType.BOAT) {
                continue;
            }
            final var history = boat.getFollowerHistory();
            if (history == null || history.controlEndTick() == Long.MIN_VALUE) {
                continue;
            }
            final Box shape = world.trustedBoatCollisionBox(boat);
            if (shape == null) {
                continue;
            }
            final float top = shape.maxY;
            final var envelope = history.envelope();
            if (envelope.assumedNow()) {
                continue;
            }
            // The set the top can be in: the envelope's render set moved by the top's offset from the render position.
            // The set itself has no slack. Only the claimed-feet test below allows one float step (precision).
            final float low = top + (envelope.renderSet().minY() - history.renderY());
            final float high = top + (envelope.renderSet().maxY() - history.renderY());
            final float precision = Math.max(player.getPositionUlp(), Math.ulp(top));
            if (!Float.isFinite(low) || !Float.isFinite(high)) {
                continue;
            }
            if (!(claimed.minX < shape.maxX && claimed.maxX > shape.minX && claimed.minZ < shape.maxZ && claimed.maxZ > shape.minZ)) {
                continue;
            }
            final float shift;
            if (claimedVertical) {
                if (claimed.minY < low - precision || claimed.minY > high + precision) {
                    continue;
                }
                shift = claimed.minY - top;
            } else {
                shift = low - top;
                if (!(shift < 0)) {
                    continue;
                }
            }

            if (first == null) {
                first = PredictionState.capture(player);
            }
            start.restore(player);
            world.setCollisionShift(boat, shift);
            boolean keep = false;
            try {
                new PredictionRunner(player).run();
                // Velocity uses the same limit as position (threshold plus float precision).
                // With a threshold of 0, "< threshold" alone refuses even an exact match.
                keep = player.verticalCollision == claimedVertical && CollisionRetry.offset(player) < acceptance
                        && player.velocity.distanceTo(player.unvalidatedTickEnd) < acceptance;
            } finally {
                world.clearCollisionShift();
            }
            final String line = "boat top retry: entity=" + boat.getRuntimeId() + " age=" + (player.tick - history.controlEndTick()) + " shift=" + shift
                    + " top=[" + low + "," + high + "] claimedY=" + claimed.minY + " kept=" + keep;
            player.getMovementTrace().log(line);
            Boar.debug(player.getSession().name() + ": [movement-debug] " + line, Boar.DebugMessage.INFO);
            if (keep) {
                return;
            }
            first.restore(player);
        }
    }
}
