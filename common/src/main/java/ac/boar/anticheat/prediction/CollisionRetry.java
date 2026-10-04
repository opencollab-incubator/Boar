package ac.boar.anticheat.prediction;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.check.impl.prediction.Prediction;
import ac.boar.anticheat.config.Config;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

public final class CollisionRetry {

    private static final float MIN_START_OFFSET = 1.0E-4F;
    private static final float MAX_HIGHER_START = 0.05F;
    private static final float COLLISION_EPSILON = 1.0E-5F;

    private CollisionRetry() {
    }

    private record Outcome(boolean collidedX, boolean collidedZ, boolean vertical, boolean onGround, boolean steppedUp) {
        static Outcome of(final BoarPlayer player) {
            final Vec3 asked = player.beforeCollision, moved = player.afterCollision;
            final boolean collidedX = Math.abs(asked.x - moved.x) >= COLLISION_EPSILON;
            final boolean collidedZ = Math.abs(asked.z - moved.z) >= COLLISION_EPSILON;
            final boolean steppedUp = moved.y > Math.max(asked.y, 0.0F) + COLLISION_EPSILON;
            return new Outcome(collidedX, collidedZ, player.verticalCollision, player.onGround, steppedUp);
        }

        boolean horizontal() {
            return this.collidedX || this.collidedZ;
        }
    }

    public static void attempt(final BoarPlayer player, final PredictionState start) {
        final Config config = Boar.getConfig();
        final float maxOffset = config.retryMaxOffset();
        if (!config.retryEnabled() || maxOffset <= 0 || player.disableMitigations() || player.getFlagTracker().has(EntityFlag.GLIDING)) {
            return;
        }

        final Prediction prediction = (Prediction) player.getCheckHolder().get(Prediction.class);
        if (prediction == null || player.tick < 10 || !prediction.shouldDoFail()) {
            return;
        }

        final float firstOffset = offset(player);
        if (firstOffset < config.alertThreshold() + player.getPositionUlp()) {
            return;
        }

        if (player.bestPossibility.getType() == VectorType.VELOCITY) {
            log(player, "skipped reason=knockback tick=" + player.tick);
            chat(player, "§eretry skipped at sim tick " + player.simulationFrame + ": knockback tick");
            return;
        }

        final int cooldown = config.retryCooldownTicks();
        if (cooldown > 0 && player.lastCollisionRetryTick != Long.MIN_VALUE && player.tick - player.lastCollisionRetryTick < cooldown) {
            log(player, "skipped reason=cooldown tick=" + player.tick + " lastKept=" + player.lastCollisionRetryTick);
            chat(player, "§eretry skipped at sim tick " + player.simulationFrame + ": cooldown (" + (player.tick - player.lastCollisionRetryTick) + "/" + cooldown + " ticks)");
            return;
        }

        final String name = player.getSession().name();
        final Vec3 clientStart = player.prevUnvalidatedPosition;
        final float startOffset = clientStart.distanceTo(start.position());
        if (startOffset < MIN_START_OFFSET) {
            return;
        }
        final float maxStartOffset = config.retryMaxStartOffset();
        if (startOffset > maxStartOffset) {
            log(player, "skipped reason=start-too-far tick=" + player.tick + " startOffset=" + startOffset + " clientStart=" + clientStart);
            chat(player, "§cretry capped at sim tick " + player.simulationFrame + ": start " + startOffset + " away (max " + maxStartOffset + ")");
            return;
        }

        // Don't try to run a secondary lenience sim if the player is starting inside a block
        final float horizontalContract = Math.max(1.0E-4F, 2.0F * player.getPositionUlp());
        final Box startBox = player.dimensions.getBoxAt(clientStart).contract(horizontalContract, 1.0E-4F, horizontalContract);
        final boolean startFree = player.compensatedWorld.noCollision(startBox);
        if (!startFree) {
            log(player, "skipped reason=start-inside-block tick=" + player.tick + " clientStart=" + clientStart);
            chat(player, "§eretry skipped at sim tick " + player.simulationFrame + ": start inside a block");
            return;
        }

        final PredictionState first = PredictionState.capture(player);
        final Outcome firstOutcome = Outcome.of(player);

        start.restore(player);
        player.setPos(clientStart.clone(), false);
        player.getMovementTrace().log("retry: running the tick again from the client's last position " + clientStart + " (first run offset=" + firstOffset + " startOffset=" + startOffset + ")");
        new PredictionRunner(player).run();

        final float retryOffset = offset(player);
        final Outcome retryOutcome = Outcome.of(player);
        final boolean claimedHorizontal = player.getInputData().contains(PlayerAuthInputData.HORIZONTAL_COLLISION);
        final boolean claimedVertical = player.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION);

        final String reason;
        if (retryOutcome.equals(firstOutcome)) {
            reason = "same-collision";
        } else if (clientStart.y - start.position().y > MAX_HIGHER_START && (retryOutcome.onGround() && !firstOutcome.onGround() || retryOutcome.steppedUp() && !firstOutcome.steppedUp())) {
            reason = "higher-start"; // Only the higher start landed (client pos) which could mean some cheater is attempting to get an extra jump height
        } else if (retryOutcome.horizontal() != claimedHorizontal || retryOutcome.vertical() != claimedVertical) {
            reason = "client-flags-differ";
        } else if (retryOffset > maxOffset) {
            reason = "offset";
        } else {
            reason = null;
        }

        final String info = "tick=" + player.tick + " firstOffset=" + firstOffset + " retryOffset=" + retryOffset
                + " startOffset=" + startOffset + " startDy=" + (clientStart.y - start.position().y) + " first=" + firstOutcome + " retry=" + retryOutcome
                + " claimed=(h=" + claimedHorizontal + ",v=" + claimedVertical + ")";
        if (reason == null) {
            player.getMovementTrace().log("retry: kept the second run, pos=" + player.position);
            player.lastCollisionRetryTick = player.tick;
            Boar.debug(name + ": [movement-debug] collision retry passed " + info + " pos=" + player.position, Boar.DebugMessage.INFO);
            chat(player, "§aretry passed at sim tick " + player.simulationFrame + " (miss " + retryOffset + ", was " + firstOffset + ", start " + startOffset + " away)");
            return;
        }

        first.restore(player);
        log(player, "failed reason=" + reason + " " + info);
        chat(player, "§cretry failed at sim tick " + player.simulationFrame + ": " + reason + " (miss " + retryOffset + ")");
    }

    private static float offset(final BoarPlayer player) {
        final UncertainRunner uncertainRunner = new UncertainRunner(player);
        float offset = player.position.distanceTo(player.unvalidatedPosition);
        offset -= uncertainRunner.extraOffset(offset);
        return offset;
    }

    private static void chat(final BoarPlayer player, final String message) {
        if (Boar.getInstance().getPlatform().developerDebug()) {
            player.getSession().sendMessage(message);
        }
    }

    private static void log(final BoarPlayer player, final String message) {
        player.getMovementTrace().log("retry: " + message);
        Boar.debug(player.getSession().name() + ": [movement-debug] collision retry " + message, Boar.DebugMessage.INFO);
    }
}
