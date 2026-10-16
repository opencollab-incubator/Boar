package ac.boar.anticheat.prediction;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.BoatTakeoverTransition;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.prediction.engine.data.VectorType;
import ac.boar.anticheat.prediction.vehicle.FollowerHistory;
import ac.boar.anticheat.prediction.vehicle.PositionSet;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.ArrayList;
import java.util.List;

// Things that I don't even bother account for...
@RequiredArgsConstructor
public class UncertainRunner {
    private final BoarPlayer player;

    // Slack for float rounding of a yaw in degrees: 4 float steps at 180.
    private static final float TAKEOVER_YAW_SLACK = 4 * Math.ulp(180.0F);
    // The deepest the start box may sit inside the floor for knownBoatShiftClear to still lift the sweep out of it.
    private static final float MAX_SETTLE_SINK = 0.1F;

    // The takeover methods below skip null checks for these, because they always hold:
    // - start is never null: their only caller, BoatTicker.takeover, is only called with a start (see BoatTicker.tick).
    // - start.transition.entity is never null: BoatTakeoverTransition.arm makes no transition without an entity.
    public record BoatTakeoverStart(BoatTakeoverTransition transition, Vec3 position, Box box) {}

    /** A possible start position only. The caller must run the normal boat tick from it and check the result. */
    public BoatTakeoverStart boatTakeoverStart(final EntityCache entity) {
        final var ride = this.player.vehicleData;
        if (ride.boatTakeover == null || !ride.boatTakeover.tryUse(this.player)) {
            return null;
        }
        final BoatTakeoverTransition transition = ride.boatTakeover;
        final String reason = boatTakeoverStartDecline(ride, transition, entity);
        if (reason != null) {
            // entity is never null: BoatTicker.tick returns before this when it has no boat.
            takeoverLog("declined-start reason=" + reason + " inputCount=" + this.player.acceptedInputCount
                    + " lastAcceptedBoatTakeoverInput=" + this.player.lastAcceptedBoatTakeoverInput + " motionOverride=" + ride.motionOverride
                    + " serverMotionActive=" + ride.serverMotionActive + " motionAckTick=" + entity.getAcknowledgedMotionTick());
            return null;
        }
        return new BoatTakeoverStart(transition, this.player.position.clone(), ride.box.clone());
    }

    /** Keep the checks in this order. The first one that fails is the reason we return. Others may fail too. */
    private String boatTakeoverStartDecline(final VehicleData ride, final BoatTakeoverTransition transition, final EntityCache entity) {
        if (!ride.inputClaimsClientPrediction) {
            return "not-vehicle-format";
        }
        if (ride.predictedVehicleUniqueId != ride.vehicleUniqueId) {
            return "predicted-identity";
        }
        if (!ride.vehicleRotationPresent) {
            return "missing-rotation";
        }
        if (!Float.isFinite(ride.vehicleRotation.getX()) || !Float.isFinite(ride.vehicleRotation.getY())) {
            return "nonfinite-rotation";
        }
        if (ride.invalidVehicleInput) {
            return "invalid-input";
        }
        if (ride.pendingLeave) {
            return "pending-leave";
        }
        if (ride.needsVehicleRestart) {
            return "restart";
        }
        if (entity != this.player.compensatedWorld.getEntity(ride.vehicleRuntimeId)) {
            return "cached-actor";
        }
        if (entity.getUniqueId() != ride.vehicleUniqueId) {
            return "actor-identity";
        }
        if (!entity.hasFirstPlayerPassenger(this.player.uniqueEntityId)) {
            return "first-passenger";
        }
        if (this.player.disableMitigations()) {
            return "mitigations-disabled";
        }
        if (this.player.dead) {
            return "dead";
        }
        if (this.player.inLoadingScreen) {
            return "loading";
        }
        if (this.player.pendingDimensionSwitches > 0) {
            return "dimension-switch";
        }
        if (this.player.insideUnloadedChunk) {
            return "unloaded-chunk";
        }
        if (this.player.getTeleportUtil().isTeleporting()) {
            return "teleporting";
        }
        if (this.player.getTeleportUtil().hasPendingCorrection()) {
            return "pending-correction";
        }
        if (this.player.getTeleportUtil().isCorrectionCooldown()) {
            return "correction-cooldown";
        }
        if (this.player.getTeleportUtil().getLastTeleportSince(Long.MIN_VALUE) != transition.teleportAtArm) {
            return "changed-teleport";
        }
        // A server push at mount (SetEntityMotion on this boat, acked this tick or the last) does not block the takeover.
        // The normal run and the takeover runs start from the same start pose, because BoatTicker.mountActor overwrites the pushed velocity.
        // Recorded data from push mounts fits this model.
        // Still declined: a push that is not fresh (only serverMotionActive is left from an earlier tick), or a nonfinite one.
        // VehiclePredictionRunner.run sets motionOverride only for a fresh, finite push. It clears it after the tick (run and runDriverSwitchTick).
        // So the motion-override decline below only fires when an exception stopped that run before the clear and left an old override.
        final boolean freshMountPush = ride.motionOverride != null && MathUtil.finite(ride.motionOverride)
                && this.player.tick - entity.getAcknowledgedMotionTick() <= 1;
        if (ride.motionOverride != null && !freshMountPush) {
            return "motion-override";
        }
        if (ride.serverMotionActive && !freshMountPush) {
            return "server-motion-active";
        }
        if (this.player.bestPossibility.getType() == VectorType.VELOCITY) {
            return "velocity-vector";
        }
        if (!MathUtil.finite(this.player.position)) {
            return "nonfinite-position";
        }
        if (!MathUtil.finite(this.player.velocity)) {
            return "nonfinite-velocity";
        }
        if (!MathUtil.finite(this.player.unvalidatedPosition)) {
            return "nonfinite-claim-position";
        }
        if (!MathUtil.finite(this.player.unvalidatedTickEnd)) {
            return "nonfinite-claim-velocity";
        }
        if (ride.box == null) {
            return "missing-box";
        }
        if (entity.getDepenetrationState().flags() != 0) {
            return "depenetration";
        }
        if (!entity.getDepenetrationState().oneWayBoxes().isEmpty()) {
            return "one-way-collision";
        }
        if (this.player.lastAcceptedBoatTakeoverInput != Long.MIN_VALUE
                && this.player.acceptedInputCount - this.player.lastAcceptedBoatTakeoverInput < BoatTakeoverTransition.TAKEOVER_COOLDOWN_INPUTS) {
            return "repeat-input-cooldown";
        }
        return null;
    }

    /**
     * How far to shift our start so this tick ends at the claimed position, or null for no shift.
     * The client's start can be another point on the boat's recent path, because its interpolation timing differs from ours.
     * - The client's start (our start plus the end difference) must be inside the set FollowerEnvelope kept at the first-control step
     *   (FollowerHistory.envelopeControlSet). The set is built only from times Boar saw, plus float slack, nothing else.
     *   There is no distance cap on top: a cap below the set would only decline a true start (a fast boat on ice) and send a correction.
     * - The shift must not move the boat through blocks (knownBoatShiftClear).
     * - The normal boat tick from the shifted start must then end at the claim (acceptBoatTakeover).
     * Why a set and not a fixed radius: in-game testing showed true client starts that a fixed radius declined, and none outside the set.
     * <p>
     * prior: null on the first try. On the second try (BoatTicker.takeover) it is the shift a run already used. The new shift is prior plus what
     * is left between that run's end and the claim. All checks apply to the total shift.
     * Why a second try: pushing a box out of the floor (depenetration) can move two starts differently, so the first shift can miss.
     * In-game testing showed this in flowing water: our start was pushed up and forward onto a block edge, the client's start straight up.
     */
    public Vec3 boatTakeoverShift(final BoatTakeoverStart start, final Vec3 prior) {
        final Vec3 rest = this.player.unvalidatedPosition.subtract(this.player.position);
        final Vec3 difference = prior == null ? rest : prior.add(rest);
        final float acceptance = this.player.getPosAcceptanceLimit();
        // A finite difference also means a finite rest: a NaN or infinite part of rest stays NaN or infinite after adding prior.
        if (!MathUtil.finite(difference) || rest.length() < acceptance) {
            return null;
        }
        final float excess = this.takeoverSetExcess(start, difference);
        if (!(excess <= 0)) {
            takeoverLog("declined-set offset=" + difference + " excess=" + excess);
            return null;
        }
        if (!this.shiftClear(start, difference)) {
            takeoverLog("declined-shift-collision");
            return null;
        }
        return difference;
    }

    /**
     * Shift to the center of the envelope's set. Used when the end-difference shift was declined or missed.
     * Why: our own start (the nominal interpolation) can be off the set, and physics from a wrong start can miss in another axis.
     * In-game testing showed this on ice: our boat sat too low, the floor push-out moved it sideways, and the client's boat rested at the set's single point.
     * The normal boat tick from this start must still end at the claim (acceptBoatTakeover).
     * Null when the set is not trusted, the shift is below acceptance, or it would move the boat through blocks.
     */
    public Vec3 boatTakeoverCenterShift(final BoatTakeoverStart start) {
        final EntityCache entity = start.transition.entity;
        final var set = takeoverSet(entity.getFollowerHistory());
        if (set == null) {
            return null;
        }
        final Vec3 difference = new Vec3(set.centerX() - start.position.x, set.centerY() - entity.getYOffset() - start.position.y,
                set.centerZ() - start.position.z);
        final float acceptance = this.player.getPosAcceptanceLimit();
        if (!MathUtil.finite(difference) || difference.length() < acceptance) {
            return null;
        }
        if (!this.shiftClear(start, difference)) {
            takeoverLog("declined-center-shift-collision offset=" + difference);
            return null;
        }
        takeoverLog("center-candidate offset=" + difference);
        return difference;
    }

    // How far the client's start (our start plus the end difference) is outside the envelope's set at the first-control step. 0 or less is inside.
    // Physics moves both starts the same way, unless a collision differs. No trusted set gives +infinity (outside).
    // difference is finite: the only caller, boatTakeoverShift, checks that first.
    private float takeoverSetExcess(final BoatTakeoverStart start, final Vec3 difference) {
        final EntityCache entity = start.transition.entity;
        final var set = takeoverSet(entity.getFollowerHistory());
        if (set == null) {
            return Float.POSITIVE_INFINITY;
        }
        final float x = start.position.x + difference.x, y = start.position.y + entity.getYOffset() + difference.y, z = start.position.z + difference.z;
        return set.excess(x, y, z, PositionSet.slack(x, y, z));
    }

    // The set the client's start can be in at the first-control step, or null when the envelope was not trusted at that step.
    private static PositionSet takeoverSet(final FollowerHistory history) {
        return history != null && history.envelopeControlTrusted() ? history.envelopeControlSet() : null;
    }

    /**
     * Takeover yaw: how many degrees to turn our start yaw so this tick ends at the boat yaw the client sent (VehicleRotation yaw). 0 means no turn.
     * Why: on a turning boat the client's interpolation timing gives it a different yaw at takeover, the same way it gives a different start position
     * (boatTakeoverShift).
     * - The turn must fit the envelope's yaw range at the first-control step (FollowerHistory.envelopeControlYawOffsets): the yaws the client can
     *   show at that step, as offsets from our own yaw. No range, or a range the envelope does not trust, declines the turn.
     *   A declined turn, or one outside the range, is not taken, and the position-only try runs as before.
     * - Our yaw change in one tick does not depend on the start yaw (BoatTicker.simulate), so the end difference is also the start difference.
     *   This needs VehicleRotation to be the yaw after the client's tick. acceptBoatTakeover still checks the end yaw.
     * In-game testing showed turning takeovers got a correction a few ticks later without this, and none with it.
     */
    public float boatTakeoverYawShift(final BoatTakeoverStart start, final float predictedYaw) {
        final float shift = MathUtil.wrapDegrees(this.player.vehicleData.vehicleRotation.getY() - predictedYaw);
        if (!Float.isFinite(shift) || Math.abs(shift) <= TAKEOVER_YAW_SLACK) {
            return 0;
        }
        final var history = start.transition.entity.getFollowerHistory();
        final float[] range = history == null || !history.envelopeControlTrusted() ? null : history.envelopeControlYawOffsets();
        if (range == null) {
            takeoverLog("declined-yaw shift=" + shift + " range=none");
            return 0;
        }
        final float lo = range[0] - TAKEOVER_YAW_SLACK, hi = range[1] + TAKEOVER_YAW_SLACK;
        if (shift < lo || shift > hi) {
            takeoverLog("declined-yaw shift=" + shift + " range=[" + lo + "," + hi + "]");
            return 0;
        }
        return shift;
    }

    public boolean acceptBoatTakeover(final float yawShift) {
        final float offset = this.player.position.distanceTo(this.player.unvalidatedPosition);
        final float velocityError = this.player.velocity.distanceTo(this.player.unvalidatedTickEnd);
        final float yawError = Math.abs(MathUtil.wrapDegrees(this.player.vehicleData.vehicleRotation.getY() - this.player.vehicleData.yaw));
        // Normal physics from the new start must reach the claimed position. Never copy the claim here.
        // The normal post path then handles the sent move from this same start. A turned start must also end at the claimed yaw.
        final boolean accepted = Float.isFinite(offset) && MathUtil.finite(this.player.velocity) && offset < this.player.getPosAcceptanceLimit()
                && (yawShift == 0 || yawError <= TAKEOVER_YAW_SLACK);
        takeoverLog((accepted ? "accepted" : "declined-replay") + " positionError=" + offset + " velocityError=" + velocityError + " yawShift=" + yawShift
                + " yawError=" + yawError);
        if (accepted) {
            this.player.lastAcceptedBoatTakeoverInput = this.player.acceptedInputCount;
        }
        return accepted;
    }

    private void takeoverLog(final String decision) {
        final String line = "boat takeover uncertainty: " + decision + " tick=" + this.player.tick;
        this.player.getMovementTrace().log(line);
        if (Boar.getConfig().debugMode()) {
            Boar.debug(this.player.getSession().name() + ": " + line, Boar.DebugMessage.INFO);
        }
    }

    // The box swept from the start box to the box moved by difference (both 1.0E-4 smaller) must be clear (knownBoatShiftClear).
    private boolean shiftClear(final BoatTakeoverStart start, final Vec3 difference) {
        final Box before = start.box;
        final Box sweep = before.union(before.offset(difference.x, difference.y, difference.z));
        return this.knownBoatShiftClear(sweep.contract(1.0E-4F), before.contract(1.0E-4F));
    }

    // The shift must not move the boat into anything. A boat can rest slightly inside the floor (start box sunk into blocks under it),
    // and the sweep would then fail on that floor alone. So the sweep is first lifted by that sink depth, if it is at most MAX_SETTLE_SINK.
    // Anything still in the way declines the shift.
    // Why: in-game testing in flowing water showed a start pose 0.018 inside the floor; shifts inside the set were then declined by that floor.
    private boolean knownBoatShiftClear(final Box sweep, final Box start) {
        try (final var reads = this.player.compensatedWorld.trackBlockReads()) {
            float sink = 0;
            for (final Box collider : this.player.compensatedWorld.collectColliders(this.player.compensatedWorld.getEntityCollisions(start), start)) {
                if (collider.minY < start.minY && collider.maxY > start.minY) {
                    sink = Math.max(sink, collider.maxY - start.minY);
                }
            }
            if (sink > MAX_SETTLE_SINK) {
                sink = 0;
            }
            final Box lifted = sink > 0 ? sweep.offset(0, sink + 1.0E-4F, 0) : sweep;
            return this.player.compensatedWorld.collectColliders(this.player.compensatedWorld.getEntityCollisions(lifted), lifted).isEmpty() && reads.known();
        }
    }

    // For now this will only be use for pushing player out of block, mojang making my life harder by make it differ from JE ofc.
    /**
        Note: for anticheat developers on other platform, this code if when the player have the flag PUSH_TOWARDS_CLOSEST_SPACE
        if there isn't, the player can be instantly push out by bruteforce direction to push out then getting the direction with the smallest value
        (<a href="https://github.com/GeyserMC/Geyser/blob/43467f7d19d5a86611c47b2d42b03357b319aac4/core/src/main/java/org/geysermc/geyser/translator/collision/BlockCollision.java#L77">...</a>)
        I can't remember the max value to be pushed out, maybe around 0.8+? but yep. You can either do uncertainty or calculate it that way.
        However, without the flag getting into crawling mode is annoying and who knows is mojang going to break it or not.
    **/
    public void uncertainPushTowardsTheClosetSpace() {
        // Close enough, no uncertainty here, we're sure.
        if (player.velocity.distanceTo(player.unvalidatedTickEnd) <= 1.0E-4) {
            // TODO: Should we enforce this to prevent cheater from not being pushed out of block?
            return;
        }

        // Now let's account for pushing out of block uncertainty.
        // Since block push will only affect X and Z direction so this case no need to account for push, player velocity is wrong.
        if (Math.abs(player.unvalidatedTickEnd.y - player.velocity.y) > 1.0E-4) {
            return;
        }
        // Assuming that we're missing the push out of block velocity, then subtracting velocity will do the job.
        Vec3 pushTowardsClosetSpaceVel = player.unvalidatedTickEnd.subtract(player.velocity);

        // The push towards velocity should ALWAYS be normalized in a way that the squared length will always be smaller than 0.01 or at least equals (unless small floating point errors).
        // So if the player push towards velocity is any larger than this... They're cheating.
        if (pushTowardsClosetSpaceVel.horizontalLengthSquared() > 0.01 + 1.0E-3F) {
            return;
        }

        // Let's check to see if the player is actually inside a block...
        final List<Box> collisions = player.compensatedWorld.collectColliders(new ArrayList<>(), player.boundingBox.contract(1.0E-3F));
        if (collisions.isEmpty()) {
            return;
        }

        final int signX = MathUtil.sign(pushTowardsClosetSpaceVel.x), signZ = MathUtil.sign(pushTowardsClosetSpaceVel.z);
        final int targetX = GenericMath.floor(player.position.x) + signX, targetZ = GenericMath.floor(player.position.z) + signZ;
        final Box box = new Box(targetX, player.boundingBox.minY, targetZ, targetX + 1, player.boundingBox.maxY, targetZ + 1).contract(1.0E-3F);

        // The direction player is trying to move to is also not empty, so this is likely wrong.
        // TODO: Not reliable.... Well let's rely on vanilla to prevent phase then...
//        if (!player.compensatedWorld.noCollision(box)) {
//            return;
//        }

        // TODO: Enforce this further.
        player.getMovementTrace().log("uncertainty: push out of block, accepted client vel=" + player.unvalidatedTickEnd
                + " (push=" + pushTowardsClosetSpaceVel + ")");
        player.velocity = player.unvalidatedTickEnd.clone();
    }

    public float extraOffset(float offset) {
        float extra = 0;

        if (player.getFlagTracker().has(EntityFlag.GLIDING)) {
            extra += 8.0E-4F; // gliding accuracy is... yuck.

            if (offset <= 8.0E-4 && player.glideBoostTicks >= 0) {
                extra = offset;
            }
        }

        return extra;
    }
}
