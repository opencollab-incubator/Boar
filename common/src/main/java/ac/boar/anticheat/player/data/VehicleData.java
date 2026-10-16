package ac.boar.anticheat.player.data;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.check.impl.prediction.Prediction;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.vehicle.VehicleTicker;
import ac.boar.anticheat.teleport.data.TeleportData;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.Objects;

public class VehicleData {

    // A matched claim: a vehicle position the client sent (vehicle format) that our physics also reached this tick, and that passed normal
    // position acceptance. It is the exit origin for the follower death of a mount the client drove (a saddled donkey or mule).
    // The client leaves such a mount from its own last vehicle position. In-game testing showed the server's position for the mount lags
    // behind while it moves. A matched claim is never a start point for movement.
    // The physics outcome (horsePhysicsOutcome) is our committed physics result for the input. The claim is kept only if it matches it.
    // Both are a TickPosition: player.tick, the input frame and a position.
    // TickPosition and FrozenCamelExitPose do not copy the ride, its actor or its IDs. Each lives in a private field of the ride that made it,
    // only for that ride's actor (isMountedEntity). Those ride fields are written once (BoarDefaultAcknowledgments.handleVehicleSet makes a new
    // VehicleData per ride, and attachLateVehicle only fills a null mountedEntity), so a copy would always equal them.
    private record TickPosition(long tick, long frame, float x, float y, float z) {}

    public long vehicleRuntimeId;
    public long vehicleUniqueId;
    // The actor we got an ack for on this ride. Unlike lastKnownEntity, it does not follow a runtime ID being replaced.
    public EntityCache mountedEntity;

    // Kept on the ride, so a new ride cannot inherit an old interpolation target or old completion state.
    public HorseForcedMove horseForcedMove;
    public boolean horseForcedCompletionSeen;
    // Made only for this ride's horse (matchesForcedHorseEntity), so it does not keep the actor.
    public record HorseForcedMove(Vec3 position, float yaw, boolean onGround) {}

    public boolean matchesForcedHorseEntity(final EntityCache entity) {
        return this.type == VehicleType.HORSE && isMountedEntity(entity) && "minecraft:horse".equals(entity.getDefinition().identifier());
    }

    // An actor we already got an ack for cannot be swapped for another actor that just reuses the same runtime ID.
    public boolean matchesMountedEntity(final EntityCache entity) {
        return entity != null && (this.mountedEntity == null || isMountedEntity(entity));
    }

    // The ride's own actor: the one we got an ack for, with the ride's runtime ID and unique ID.
    // An EntityCache always has a definition (the field is final, and CompensatedWorld.addToCache returns null without one).
    // So callers read it without a null check.
    public boolean isMountedEntity(final EntityCache entity) {
        return entity != null && entity == this.mountedEntity && entity.getRuntimeId() == this.vehicleRuntimeId && entity.getUniqueId() == this.vehicleUniqueId;
    }

    // The ride's own actor (isMountedEntity) that is also still the visible actor for the ride's runtime ID.
    public boolean isLiveMount(final BoarPlayer player, final EntityCache entity) {
        return isMountedEntity(entity) && player.compensatedWorld.getEntity(this.vehicleRuntimeId) == entity;
    }

    // The player rides this boat (ride is not null, boat is the ride's own actor: isMountedEntity) and is its first passenger.
    // The client's movement ack checks use the first passenger, not the steering seat (EntityCache.hasFirstPlayerPassenger).
    public static boolean isFirstPassengerOfBoat(final BoarPlayer player, final VehicleData ride, final EntityCache boat) {
        return ride != null && ride.type == VehicleType.BOAT && ride.isMountedEntity(boat) && boat.hasFirstPlayerPassenger(player.uniqueEntityId);
    }

    public VehicleType type = VehicleType.OTHER;
    // True when the player is the rider in the controlling seat (EntityCache.isControlledBy). No link type is read.
    // Set at the mount (handleVehicleSet, attachLateVehicle), then by the passenger-link and metadata acks (BoarDefaultAcknowledgments.updateDriver).
    public boolean driver;
    public BoatTakeoverTransition boatTakeover;

    // The client sent InteractPacket LEAVE_VEHICLE. We stay in the vehicle until the server removes the link.
    // A link to the same vehicle sent after the request (pendingLeaveNs, System.nanoTime() at receive) means the server refused it.
    // That clears pendingLeave.
    public boolean pendingLeave;
    public long pendingLeaveNs;

    // After a leave request (pendingLeave), horseJumpInput already holds the client's reset (VehiclePackets), so keep it.
    public void invalidateJumpInputUnlessLeaving(final BoarPlayer player) {
        if (!this.pendingLeave) {
            player.horseJumpInput.invalidate();
        }
    }

    // The client leaves on its own on the first input after the request, so the local exit (AuthInputPackets.localExitStart) is decided once per request.
    public boolean localExitDecided;

    // The input's IN_CLIENT_PREDICTED_IN_VEHICLE flag: the client says it predicts the vehicle itself. Then the AuthInput position is the
    // vehicle position plus the vehicle offset. Not the same as clientSimulatesRide, which Boar decides from server state.
    public boolean inputClaimsClientPrediction;
    // A death exit is decided once per ride, after its first vehicle-format input.
    public boolean horseDeathInputSeen, horseDeathAuthorized, horseDeathDecided;

    public boolean isHorseDeathExitMount(final BoarPlayer player, final EntityCache entity) {
        return this.type == VehicleType.HORSE && isLiveMount(player, entity)
                && ("minecraft:horse".equals(entity.getDefinition().identifier())
                || "minecraft:skeleton_horse".equals(entity.getDefinition().identifier())
                || "minecraft:zombie_horse".equals(entity.getDefinition().identifier()));
    }

    // Follower death exit: the server killed the ridden pig, donkey, mule, camel, llama or strider.
    // The client ends the ride by itself a few inputs later (death counter 2 removes the passengers).
    // Until then it sends rows at the seat with no flags. Then it sends one row at the native exit position.
    // In-game testing on a donkey and a mule showed: seat, seat, floor.
    // The input budget counts every player.tick input from the first authorization. It is never moved.
    public static final int FOLLOWER_DEATH_EXIT_INPUTS = 4;
    public boolean followerDeathAuthorized, followerDeathDecided;
    public long followerDeathDeadline = Long.MIN_VALUE;
    // Position at the first death ack. Rotation-only updates cannot change it.
    // Once the position changes later, followerDeathPositionChanged stays set.
    public Vec3 followerDeathPosition;
    public boolean followerDeathPositionChanged;

    private TickPosition horsePhysicsOutcome;
    private TickPosition lastMatchedClaim;
    private TickPosition followerDeathClaim;
    private FollowerDeathGeometry followerDeathGeometry;
    public record StationaryCamelExit(Vector3f actor, float yaw) {}
    private record FrozenCamelExitPose(StationaryCamelExit pose, Vector3f seat, EntityDimensions actorDimensions, EntityDimensions playerDimensions,
                                       float actorOffset, float riderOffset) {}
    private FrozenCamelExitPose frozenCamelExitPose;
    private record FollowerDeathGeometry(Vector3f seat, EntityDimensions actorDimensions, EntityDimensions playerDimensions, float yOffset) {}
    private long matchedClaimBlockedUntilTick = Long.MIN_VALUE;

    public void beginMatchedClaimAttempt() {
        this.horsePhysicsOutcome = null;
        this.lastMatchedClaim = null;
    }

    public void invalidateMatchedClaim(final long throughTick) {
        beginMatchedClaimAttempt();
        this.followerDeathClaim = null;
        this.followerDeathGeometry = null;
        this.frozenCamelExitPose = null;
        this.matchedClaimBlockedUntilTick = Math.max(this.matchedClaimBlockedUntilTick, throughTick);
    }

    public void markHorsePhysicsOutcome(final BoarPlayer player, final EntityCache entity) {
        if (player.vehicleData != this || player.tick <= this.matchedClaimBlockedUntilTick || !isLiveMount(player, entity) || this.ticks <= 0
                || this.needsVehicleRestart || !MathUtil.finite(player.position) || !MathUtil.finite(player.velocity)) {
            return;
        }
        this.horsePhysicsOutcome = new TickPosition(player.tick, player.simulationFrame, player.position.x, player.position.y, player.position.z);
    }

    // Called only by LegacyAuthInputPackets.doPostPrediction, on player.vehicleData. updateFromInput already read this packet's vehicle flag and
    // predicted vehicle into this ride. A ride made later in the same input has inputClaimsClientPrediction false, so it gets no claim.
    public void acceptMatchedClaim(final BoarPlayer player, final PlayerAuthInputPacket packet, final float rawOffset, final boolean ordinaryAcceptance) {
        final TickPosition outcome = this.horsePhysicsOutcome;
        this.lastMatchedClaim = matchedClaim(player, packet, rawOffset, ordinaryAcceptance, outcome);
        // In-game testing showed the client can still drive for a tick after the death ack. It then leaves from that later position,
        // so this input's claim replaces the one frozen at the ack. No claim here means we did not accept this input, so the frozen one is stale too.
        // No followerDeathDecided check: the two are never both set (see canEndFollowerDeath).
        if (this.followerDeathAuthorized) {
            freezeFollowerDeathClaim(player, this.mountedEntity, false);
        }
    }

    private TickPosition matchedClaim(final BoarPlayer player, final PlayerAuthInputPacket packet, final float rawOffset, final boolean ordinaryAcceptance,
                                      final TickPosition outcome) {
        final var prediction = (Prediction) player.getCheckHolder().get(Prediction.class);
        // Our physics result is for this exact input, and the input was accepted the normal way.
        if (!ordinaryAcceptance || outcome == null || outcome.tick != player.tick || outcome.frame != packet.getTick()
                || player.tick <= this.matchedClaimBlockedUntilTick || player.tick < 10) {
            return null;
        }
        // The Prediction check could flag this input (shouldDoFail), mitigations are on, and the player is alive.
        if (prediction == null || !prediction.shouldDoFail() || player.disableMitigations() || player.dead) {
            return null;
        }
        // The ride: no parked teleport or restart, the client says it predicts this vehicle,
        // and the player drives the ride's own live actor as its first passenger.
        if (this.parkedTeleport != null || this.needsVehicleRestart || !this.inputClaimsClientPrediction || !this.driver
                || this.predictedVehicleUniqueId != this.vehicleUniqueId || !VehicleTicker.clientSimulates(player, this)
                || !isLiveMount(player, this.mountedEntity) || !this.mountedEntity.hasFirstPlayerPassenger(player.uniqueEntityId)) {
            return null;
        }
        // rawOffset is a distance (Vec3.distanceTo in LegacyAuthInputPackets.doPostPrediction, the only caller), so it is never -Infinity.
        // This test alone also rejects NaN and +Infinity.
        if (!(rawOffset < player.getPosAcceptanceLimit())) {
            return null;
        }
        // The packet values we rely on are present and finite.
        if (!MathUtil.finite(packet.getPosition()) || !MathUtil.finite(packet.getDelta()) || !MathUtil.finite(packet.getRotation())
                || packet.getMotion() == null || !Float.isFinite(packet.getMotion().getX()) || !Float.isFinite(packet.getMotion().getY())
                || packet.getVehicleRotation() == null || !Float.isFinite(packet.getVehicleRotation().getX())
                || !Float.isFinite(packet.getVehicleRotation().getY())) {
            return null;
        }
        // A distance is never -Infinity, so this test alone also rejects NaN and +Infinity.
        final float physicalOffset = new Vec3(outcome.x, outcome.y, outcome.z).distanceTo(new Vec3(packet.getPosition()));
        if (!(physicalOffset < player.getPosAcceptanceLimit())) {
            return null;
        }
        return new TickPosition(player.tick, packet.getTick(), packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ());
    }

    // Call with the first death snapshot (firstAck), or with a later accepted input (acceptMatchedClaim). A repeated ack cannot fix missing or old data.
    // entity is always this.mountedEntity and never null. The first ack (BoarDefaultAcknowledgments.authorizeFollowerDeath) passes an actor
    // that passed isFollowerDeathExitMount (isMountedEntity). acceptMatchedClaim passes this.mountedEntity, only while followerDeathAuthorized
    // is set, which needs that same check first. mountedEntity never changes once set.
    public void freezeFollowerDeathClaim(final BoarPlayer player, final EntityCache entity, final boolean firstAck) {
        if (firstAck) {
            freezeStationaryCamelExit(player, entity);
        }
        final TickPosition claim = this.lastMatchedClaim;
        this.followerDeathClaim = claim != null && claim.tick == player.tick && claim.frame == player.simulationFrame ? claim : null;
        this.followerDeathGeometry = this.followerDeathClaim == null ? null
                : new FollowerDeathGeometry(player.seatOffset, entity.getDimensions(), player.dimensions, entity.getYOffset());
    }

    private void freezeStationaryCamelExit(final BoarPlayer player, final EntityCache entity) {
        this.frozenCamelExitPose = null;
        if (!"minecraft:camel".equals(entity.getDefinition().identifier())) {
            return;
        }
        final String reason = freezeStationaryCamelExitCandidate(player, entity);
        logFollowerDeath(player, reason == null
                ? "camel frozen exit pose=yes actor=" + this.frozenCamelExitPose.pose.actor + " yaw=" + this.frozenCamelExitPose.pose.yaw
                : "camel frozen exit pose=no reason=" + reason);
    }

    // Reached only from BoarDefaultAcknowledgments.authorizeFollowerDeath, after isFollowerDeathExitMount passed for this ride (player.vehicleData) and entity.
    private String freezeStationaryCamelExitCandidate(final BoarPlayer player, final EntityCache entity) {
        if (player.tick <= this.matchedClaimBlockedUntilTick) {
            return "matched-claim-blocked";
        }
        if (this.needsVehicleRestart || this.ticks <= 0) {
            return "ride-restart-or-unstarted";
        }
        if (VehicleTicker.clientSimulates(player, this)) {
            return "client-controlled";
        }
        if (this.parkedTeleport != null) {
            return "parked-teleport";
        }
        if (player.dead || player.inLoadingScreen || player.pendingDimensionSwitches > 0) {
            return "player-state";
        }
        if (player.getTeleportUtil().isTeleporting()) {
            return "teleporting";
        }
        if (player.getTeleportUtil().hasPendingCorrection()) {
            return "pending-correction";
        }
        if (player.getTeleportUtil().isCorrectionCooldown()) {
            return "correction-cooldown";
        }
        final var current = entity.getCurrent();
        final var history = entity.getFollowerHistory();
        if (current == null) {
            return "missing-current";
        }
        if (history == null) {
            return "missing-history";
        }
        if (!history.tickedAt(player.tick)) {
            return "stale-history";
        }
        final Vec3 position = current.getPos(), previous = current.getPrevPos(), target = entity.getAcknowledgedPositionTarget();
        if (!MathUtil.finite(position) || !MathUtil.finite(previous) || !MathUtil.finite(target) || !MathUtil.finite(player.seatOffset)) {
            return "nonfinite-geometry";
        }
        if (position.x != previous.x || position.y != previous.y || position.z != previous.z) {
            return "tracked-moving";
        }
        if (position.x != target.x || position.y != target.y || position.z != target.z) {
            return "target-differs";
        }
        final float actorY = position.y + entity.getYOffset(), yaw = entity.getYaw();
        if (!history.admitsStationaryExitPose(position.x, actorY, position.z, yaw)) {
            return "no-admitted-stationary-history";
        }
        final StationaryCamelExit pose = new StationaryCamelExit(Vector3f.from(position.x, actorY, position.z), yaw);
        this.frozenCamelExitPose = new FrozenCamelExitPose(pose, player.seatOffset, entity.getDimensions(), player.dimensions,
                entity.getYOffset(), player.getPlayerYOffset());
        return null;
    }

    // The only caller (AuthInputPackets.followerDeathExitStart) calls this on player.vehicleData, after canEndFollowerDeath passed for entity.
    // A frozen pose is only made for this ride's camel, and mountedEntity never changes once set.
    public StationaryCamelExit guardedStationaryCamelExit(final BoarPlayer player, final EntityCache entity) {
        final FrozenCamelExitPose frozen = this.frozenCamelExitPose;
        if (frozen == null || this.needsVehicleRestart
                || !Objects.equals(frozen.seat, player.seatOffset) || !Objects.equals(frozen.actorDimensions, entity.getDimensions())
                || !Objects.equals(frozen.playerDimensions, player.dimensions)
                || frozen.actorOffset != entity.getYOffset() || frozen.riderOffset != player.getPlayerYOffset()
                || player.dead || player.inLoadingScreen || player.pendingDimensionSwitches > 0
                || player.getTeleportUtil().isTeleporting() || player.getTeleportUtil().hasPendingCorrection()
                || player.getTeleportUtil().isCorrectionCooldown()) {
            this.frozenCamelExitPose = null;
            return null;
        }
        // Later corpse targets cannot replace this candidate. Its geometry is checked again at every decision.
        return frozen.pose;
    }

    // The vehicle position of the last input we accepted before the client left (the matched claim), or null when there is none.
    // Y is the vehicle format Y (a whole number on a full block). AuthInputPackets.exitStart checks the floor from it.
    // Same caller and checks before it as guardedStationaryCamelExit. A matched claim is only made from a finite packet position (matchedClaim).
    public Vec3 guardedFollowerDeathPosition(final BoarPlayer player, final EntityCache entity) {
        final TickPosition claim = this.followerDeathClaim;
        final FollowerDeathGeometry geometry = this.followerDeathGeometry;
        if (claim == null || geometry == null || this.needsVehicleRestart
                || !Objects.equals(geometry.seat, player.seatOffset) || !Objects.equals(geometry.actorDimensions, entity.getDimensions())
                || !Objects.equals(geometry.playerDimensions, player.dimensions) || geometry.yOffset != entity.getYOffset()) {
            return null;
        }
        return new Vec3(claim.x, claim.y, claim.z);
    }

    // Checks which actor and which species only. The saddle is not read here.
    // The death event ack runs before the saddle-clear metadata ack of the same batch. Measured wire order: ActorEvent 3, then SetActorData.
    // Acks are queued at send time, first in first out.
    public boolean isFollowerDeathExitMount(final BoarPlayer player, final EntityCache entity) {
        if (!isLiveMount(player, entity)) {
            return false;
        }
        final String identifier = entity.getDefinition().identifier();
        return "minecraft:pig".equals(identifier) || "minecraft:donkey".equals(identifier) || "minecraft:mule".equals(identifier)
                || "minecraft:camel".equals(identifier) || "minecraft:llama".equals(identifier) || "minecraft:strider".equals(identifier);
    }

    // At decision time, only a server-driven ride counts. A donkey or mule the client still simulates (saddle kept) is not this exit.
    // No followerDeathDecided check: the only caller (AuthInputPackets.followerDeathExitStart) runs only while followerDeathAuthorized is set.
    // The two are never both set: every write that sets followerDeathDecided clears followerDeathAuthorized,
    // and BoarDefaultAcknowledgments.authorizeFollowerDeath returns once decided.
    public boolean canEndFollowerDeath(final BoarPlayer player, final EntityCache entity) {
        return isFollowerDeathExitMount(player, entity) && !VehicleTicker.clientSimulates(player, this) && this.ticks > 0;
    }

    // Ends the follower death exit for this ride. The rider stays on.
    public void declineFollowerDeath(final BoarPlayer player, final String reason) {
        this.followerDeathAuthorized = false;
        this.followerDeathDecided = true;
        logFollowerDeath(player, "decline reason=" + reason);
    }

    public void logFollowerDeath(final BoarPlayer player, final String detail) {
        logDeath(player, "follower death", "deadline=" + this.followerDeathDeadline + " " + detail);
    }

    public boolean canEndHorseDeath(final BoarPlayer player, final EntityCache entity) {
        return isHorseDeathExitMount(player, entity) && VehicleTicker.clientSimulates(player, this) && entity.hasFirstPlayerPassenger(player.uniqueEntityId)
                && this.horseDeathInputSeen && this.inputClaimsClientPrediction && !this.horseDeathDecided;
    }

    public void logHorseDeath(final BoarPlayer player, final String detail) {
        logDeath(player, "horse death", detail);
    }

    // kind: "horse death" or "follower death". Writes the trace line, and the debug line in debug mode.
    private void logDeath(final BoarPlayer player, final String kind, final String detail) {
        final String line = kind + ": tick=" + player.tick + " runtime=" + this.vehicleRuntimeId + " unique=" + this.vehicleUniqueId + " " + detail;
        player.getMovementTrace().log(line);
        if (Boar.getConfig().debugMode()) {
            Boar.debug(player.getSession().name() + ": " + line, Boar.DebugMessage.INFO);
        }
    }

    public long predictedVehicleUniqueId;
    public boolean vehicleRotationPresent;

    // A driver switch tick that is already set uses the actor state. It does not depend on the rider-format prediction output.
    public boolean awaitingBoatFormatInput;
    public boolean riderCoordinates;
    public boolean invalidVehicleInput;
    public BoatActorState boatActorState;
    // Set only on the first controlled tick, before its interpolation and movement.
    public BoatRenderPose firstControlRenderPose;
    private boolean driverSwitchTickAvailable;
    private long driverSwitchDecisionTick = Long.MIN_VALUE, driverSwitchTick = Long.MIN_VALUE;

    // Only a server seat change can arm this driver switch tick. Input fields cannot arm it again.
    public void armDriverSwitchTick(final boolean available) {
        this.driverSwitchTickAvailable = available;
        this.driverSwitchDecisionTick = this.driverSwitchTick = Long.MIN_VALUE;
    }

    public boolean useDriverSwitchTick(final long inputTick) {
        if (this.driverSwitchDecisionTick == inputTick) {
            return this.driverSwitchTick == inputTick;
        }
        this.driverSwitchDecisionTick = inputTick;
        this.driverSwitchTick = Long.MIN_VALUE;
        if (!this.driverSwitchTickAvailable) {
            return false;
        }
        this.driverSwitchTickAvailable = false;
        if (this.driver && this.awaitingBoatFormatInput && this.ticks == 0 && !this.inputClaimsClientPrediction) {
            this.driverSwitchTick = inputTick;
            return true;
        }
        return false;
    }

    // Reads the finished rider projection for the driver switch input that an ack armed.
    // It does not use up or arm a transition from the packet fields set after prediction.
    public boolean isDriverSwitchRiderTick(final long inputTick) {
        return this.driverSwitchTick == inputTick && this.type == VehicleType.BOAT && this.driver && this.awaitingBoatFormatInput && this.ticks == 0
                && !this.inputClaimsClientPrediction && this.riderCoordinates && this.boatActorState != null;
    }

    public record BoatRenderPose(Vec3 position, float yaw) {}

    public record BoatActorState(Vec3 position, Vec3 velocity, Box box, float pitch, float yaw, float angularVelocity, boolean onGround) {}

    public Vector2f vehicleRotation = Vector2f.ZERO;

    public Vec3 position = Vec3.ZERO, prevPosition = Vec3.ZERO;
    // The client keeps the box edges as the vehicle state and gets the position back from them, so we do the same.
    public Box box;
    private EntityDimensions boxDimensions;
    public boolean onGround;

    public InputInteractionModel interactionModel = InputInteractionModel.TOUCH;
    public Vector2f motion = Vector2f.ZERO;
    public boolean up, paddleLeft, paddleRight, jumping, prevJumping;

    // The last teleport accepted while riding. It is applied on the dismount if it was the last one sent before it.
    public TeleportData parkedTeleport;
    // An input we held while waiting for the dismount teleport ack (AuthInputPackets, WAITING_FOR_TELEPORT) had HANDLE_TELEPORT.
    // The client handled the teleport on that input, so the later LEFT input must not skip its own travel.
    public boolean heldTeleportHandled;
    // An older teleport from a passenger must not mean a dismount when control changes.
    // Keep the object for an explicit REMOVE ack, which has its own matching rule.
    public TeleportData preDriverSwitchTeleport;

    public long mountTick;
    // The client's MobIsJumping flag and the pending jump power are two separate things.
    public boolean horseJumping;
    public float horsePendingScale;
    public boolean horseJumpKnown;

    // Client-local standing and stand sliding. Null means we have seen no state for this ride.
    public Boolean horseStanding, horseStandSliding;

    // The mount window ends at the first simulated travel. A rider release rules out both starts.
    public enum HorseMountWindow { NONE, PENDING, RELEASED }
    public HorseMountWindow horseMountWindow = HorseMountWindow.NONE;
    public Vec3 horseMountStart;
    // The support probe alone does not mean the horse travelled on the ground.
    public boolean horseGroundFromTravel;
    // A proposal for this input only. The client's Sliding is cleared after the position is accepted.
    public boolean pendingSlidingClear;

    // accepted already includes the mounted actor check (LegacyAuthInputPackets.doPostPrediction, the only caller).
    public void finishSlidingClear(boolean accepted) {
        if (accepted && this.pendingSlidingClear && this.mountedEntity != null) {
            this.mountedEntity.getHorseImmobility().clearSlidingAfterTravel();
        }
        this.pendingSlidingClear = false;
    }

    public void invalidateHorseStand() {
        this.horseStanding = this.horseStandSliding = null;
    }

    public void invalidateHorseJump() {
        this.horseJumpKnown = false;
    }

    /** Call only after the simulation proves the native ground flag went from FALSE to TRUE. */
    public void horseLanded() {
        this.horsePendingScale = 0;
        this.horseJumping = false;
        this.horseJumpKnown = true;
    }

    // The last actor we got an ack for. Kept for a short gap in the cache. Never replaced with a spawn we have no ack for.
    public EntityCache lastKnownEntity;
    public boolean needsVehicleRestart;
    // A server motion accepted for this tick only. The runner clears it after prediction.
    public Vec3 motionOverride;
    // An acked server push (SetActorMotion) may still be moving the vehicle.
    // VehiclePredictionRunner sets it on every client-simulated ride that gets an acked motion.
    // Cleared only by a reseed or a seat-change ack. While set, UncertainRunner declines a boat takeover.
    public boolean serverMotionActive;
    // Boar's own decision from server state: does the client simulate this vehicle (VehicleTicker.clientSimulates)? Not the input flag
    // (inputClaimsClientPrediction). Set by VehiclePredictionRunner after each vehicle tick.
    public boolean clientSimulatesRide;

    // Vehicle ticks since the mount was acknowledged. A restart (needsVehicleRestart) or a seat-change ack sets it back to 0.
    public int ticks;
    // Vehicle ticks in a row in which the vehicle did not move: position equal to prevPosition on every axis.
    // The client's local exit reads the vehicle's previous position and its last move (ExitVehicleSystemImpl::_tickExitVehicle 0x10519c508).
    // We do not know which of our last two ticks that is, so the local exit (AuthInputPackets.localExitStart) needs 2 still ticks.
    public int stillTicks;

    public void moveTo(final Vec3 position) {
        this.prevPosition = this.position;
        this.position = position;
        if (position.x == this.prevPosition.x && position.y == this.prevPosition.y && position.z == this.prevPosition.z) {
            this.stillTicks++;
        } else {
            this.stillTicks = 0;
        }
    }

    // Boat state, see BoatPaddleInputSystem and BoatMoveControlSystem on the client.
    public float yaw, yawVelocity;
    // The vehicle pitch we send in a correction.
    public float pitch;
    public final Paddle firstPaddle = new Paddle(), secondPaddle = new Paddle();
    public int paddleTick;
    // Block slowdown for the vehicle's next move (client BlockMovementSlowdownMultiplierComponent). null = none. Only BoatTicker and HorseTicker
    // use it. They never share it: a ride's type does not change once it is BOAT or HORSE (attachLateVehicle only fills a ride with no mountedEntity).
    // - Boat: set by a cobweb or powder snow the boat was inside after its last move, used up by the next move (BoatTicker.simulate).
    // - Steered horse: set by a cobweb, powder snow or sweet berry bush the horse was inside after its last move, scales the next move
    //   (HorseTicker.predictSimulation) and is cleared after it (HorseTicker.applySimulation).
    public Vec3 slowdown;

    public static final class Paddle {
        // The client constructor sets the paddle component to zero.
        public int rowStart;
        public int lastRowStart;
        public float speed;
    }

    public void setActorBox(final Box box, final EntityDimensions dimensions) {
        this.box = box;
        this.boxDimensions = dimensions;
    }

    // The box at the given position. It stays the same box while the position is the one we got from it and the size did not change.
    public Box boxAt(final Vec3 position, final EntityDimensions dimensions) {
        if (this.box == null || !dimensions.equals(this.boxDimensions) || boxPosition(this.box).distanceTo(position) != 0) {
            this.box = dimensions.getBoxAt(position.x, position.y, position.z);
            this.boxDimensions = dimensions;
        }
        return this.box;
    }

    public static Vec3 boxPosition(final Box box) {
        return new Vec3((box.minX + box.maxX) * 0.5F, box.minY, (box.minZ + box.maxZ) * 0.5F);
    }

    public void updateFromInput(final PlayerAuthInputPacket packet) {
        this.inputClaimsClientPrediction = packet.getInputData().contains(PlayerAuthInputData.IN_CLIENT_PREDICTED_IN_VEHICLE);
        this.predictedVehicleUniqueId = packet.getPredictedVehicle();

        // Null when the client just got in or out of the vehicle.
        this.vehicleRotationPresent = packet.getVehicleRotation() != null;
        if (this.vehicleRotationPresent) {
            this.vehicleRotation = packet.getVehicleRotation();
        }

        if (packet.getInputInteractionModel() != null) {
            this.interactionModel = packet.getInputInteractionModel();
        }
        this.motion = packet.getMotion();
        this.up = packet.getInputData().contains(PlayerAuthInputData.UP);
        this.paddleLeft = packet.getInputData().contains(PlayerAuthInputData.PADDLE_LEFT);
        this.paddleRight = packet.getInputData().contains(PlayerAuthInputData.PADDLE_RIGHT);
        this.prevJumping = this.jumping;
        this.jumping = packet.getInputData().contains(PlayerAuthInputData.JUMPING);
    }
}
