package ac.boar.anticheat.prediction.vehicle;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.player.data.VehicleType;
import ac.boar.anticheat.prediction.vehicle.impl.HorseTicker;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Vec3;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.math.vector.Vector2f;

@RequiredArgsConstructor
public class VehiclePredictionRunner {
    private final BoarPlayer player;

    public void run() {
        final VehicleData vehicle = this.player.vehicleData;
        // A sliding clear is proposed for one input only (VehicleData.pendingSlidingClear). Start this input without one.
        vehicle.pendingSlidingClear = false;
        final boolean awaitingBoatInput = vehicle.awaitingBoatFormatInput && vehicle.driver && vehicle.type == VehicleType.BOAT;
        if (awaitingBoatInput && vehicle.riderCoordinates && vehicle.boatActorState != null) {
            this.restoreActor(vehicle.boatActorState);
        }
        final boolean invalidInput = awaitingBoatInput && this.hasInvalidBoatInput(vehicle);
        vehicle.invalidVehicleInput = invalidInput;
        if (invalidInput) {
            vehicle.motion = Vector2f.ZERO;
            vehicle.up = vehicle.paddleLeft = vehicle.paddleRight = false;
            this.player.getMovementTrace().log("boat: invalid predicted vehicle identity or payload, kept server actor prediction");
        }
        final EntityCache lookedUp = this.player.compensatedWorld.getEntity(vehicle.vehicleRuntimeId);
        final EntityCache cache = vehicle.type == VehicleType.BOAT && !vehicle.matchesMountedEntity(lookedUp) ? null : lookedUp;
        if (cache != null) {
            vehicle.lastKnownEntity = cache;
            if (vehicle.needsVehicleRestart) {
                vehicle.needsVehicleRestart = false;
                vehicle.ticks = 0;
                vehicle.stillTicks = 0;
                vehicle.box = null;
                vehicle.yawVelocity = 0;
                vehicle.onGround = false;
                vehicle.horseJumping = false;
                vehicle.horseGroundFromTravel = false;
                vehicle.invalidateHorseJump();
                vehicle.invalidateHorseStand();
                cache.getHorseGround().markUnknown();
                vehicle.serverMotionActive = false;
                vehicle.boatActorState = null;
            }
            final Vec3 motion = cache.getAcknowledgedMotion();
            cache.setAcknowledgedMotion(null);
            if (motion != null && this.player.tick - cache.getAcknowledgedMotionTick() <= 1 && VehicleTicker.clientSimulates(this.player, vehicle)
                    && MathUtil.finite(motion)) {
                vehicle.motionOverride = motion.clone();
                vehicle.serverMotionActive = true;
                this.player.velocity = motion.clone();
            }
        }
        final EntityCache switchCache = cache != null ? cache : vehicle.matchesMountedEntity(vehicle.lastKnownEntity) ? vehicle.lastKnownEntity : null;
        if (awaitingBoatInput && vehicle.useDriverSwitchTick(this.player.tick) && switchCache != null && switchCache.getCurrent() != null) {
            this.runDriverSwitchTick(switchCache);
            return;
        }
        final Vec3 actorStart = awaitingBoatInput && vehicle.boatActorState != null ? this.player.position.clone() : null;
        final float actorStartYaw = vehicle.yaw;
        final VehicleTicker ticker = VehicleTicker.forVehicle(this.player, vehicle);

        if (vehicle.type == VehicleType.HORSE && !(ticker instanceof HorseTicker)) {
            VehicleTicker.stopHorseTravel(this.player, vehicle, "without horse travel", cache);
        }

        this.player.getMovementTrace().log("vehicle: type=" + vehicle.type + " ticker=" + ticker.name()
                + " inputClaimsClientPrediction=" + vehicle.inputClaimsClientPrediction + " pendingLeave=" + vehicle.pendingLeave
                + " vehicleRotation=" + vehicle.vehicleRotation + " motion=" + vehicle.motion
                + " paddle=(" + vehicle.paddleLeft + "," + vehicle.paddleRight + ") jumping=" + vehicle.jumping);

        vehicle.firstControlRenderPose = null;
        try {
            ticker.tick();
        } finally {
            vehicle.motionOverride = null;
        }
        vehicle.clientSimulatesRide = VehicleTicker.clientSimulates(this.player, vehicle);

        vehicle.moveTo(this.player.position.clone());
        vehicle.ticks++;

        if (vehicle.type == VehicleType.BOAT && vehicle.driver && cache != null) {
            cache.setBoatVelocity(this.player.velocity.clone());
        }
        if (awaitingBoatInput) {
            vehicle.boatActorState = new VehicleData.BoatActorState(this.player.position.clone(), this.player.velocity.clone(), vehicle.box,
                    vehicle.pitch, vehicle.yaw, vehicle.yawVelocity, vehicle.onGround);
            vehicle.riderCoordinates = !vehicle.inputClaimsClientPrediction;
            if (vehicle.riderCoordinates) {
                // On the first control tick, the render pose (before interpolation) and the physics pose (after interpolation) differ.
                // Use the render pose here. The yaw of this tick is an approximation of the client's frame-interpolated RenderRotation.
                final var render = vehicle.firstControlRenderPose;
                if (render != null) {
                    this.projectRider(render.position(), render.yaw());
                } else {
                    this.projectRider(actorStart == null ? this.player.position : actorStart, actorStart == null ? vehicle.yaw : actorStartYaw);
                }
            }
            if (invalidInput) {
                this.player.getTeleportUtil().correct();
            }
        }

        this.player.getMovementTrace().log("vehicle prediction done: predictedPos=" + this.player.position + " clientPos=" + this.player.unvalidatedPosition
                + " predictedVel=" + this.player.velocity + " clientVel=" + this.player.unvalidatedTickEnd);
    }

    // The boat-format input is invalid when its position, tick end or motion is not finite. When it claims client prediction,
    // it is also invalid for another vehicle or without a finite vehicle rotation.
    private boolean hasInvalidBoatInput(final VehicleData vehicle) {
        final boolean finitePayload = MathUtil.finite(this.player.unvalidatedPosition) && MathUtil.finite(this.player.unvalidatedTickEnd)
                && Float.isFinite(vehicle.motion.getX()) && Float.isFinite(vehicle.motion.getY());
        if (!finitePayload) {
            return true;
        }
        if (!vehicle.inputClaimsClientPrediction) {
            return false;
        }
        final boolean claimsThisVehicle = vehicle.predictedVehicleUniqueId == vehicle.vehicleUniqueId;
        return !claimsThisVehicle
                || !vehicle.vehicleRotationPresent
                || !Float.isFinite(vehicle.vehicleRotation.getX())
                || !Float.isFinite(vehicle.vehicleRotation.getY());
    }

    private void runDriverSwitchTick(final EntityCache cache) {
        final VehicleData vehicle = this.player.vehicleData;
        var switchState = cache.getDriverSwitchState();
        if (switchState == null || switchState.inputTick() != this.player.tick) {
            this.player.tickDriverSwitch(cache);
            switchState = cache.getDriverSwitchState();
        }
        final var actor = switchState.after();
        final Vec3 position = new Vec3(actor.x(), actor.y() - cache.getYOffset(), actor.z());
        final var current = cache.getCurrent();
        final var box = current.getPos().distanceTo(position) == 0 ? current.getBoundingBox() : cache.getDimensions().getBoxAt(position);
        vehicle.setActorBox(box, cache.getDimensions());
        vehicle.pitch = actor.pitch();
        vehicle.yaw = actor.yaw();
        vehicle.yawVelocity = cache.getBoatAngularVelocity();
        vehicle.onGround = actor.onGround();
        vehicle.moveTo(position.clone());
        vehicle.boatActorState = new VehicleData.BoatActorState(position, switchState.velocity().clone(), box, actor.pitch(), actor.yaw(), vehicle.yawVelocity,
                actor.onGround());
        vehicle.riderCoordinates = true;
        vehicle.clientSimulatesRide = VehicleTicker.clientSimulates(this.player, vehicle);
        vehicle.motionOverride = null;
        final var before = switchState.before();
        this.projectRider(new Vec3(before.x(), before.y() - cache.getYOffset(), before.z()), before.yaw());
        this.player.getMovementTrace().log("boat: driver switch remote tick, controlledTicks=" + vehicle.ticks + " actor=" + position
                + " keptVelocity=" + switchState.velocity());
        if (vehicle.invalidVehicleInput) {
            this.player.getTeleportUtil().correct();
        }
    }

    private void restoreActor(final VehicleData.BoatActorState actor) {
        this.player.setPos(actor.position().clone());
        this.player.velocity = actor.velocity().clone();
        final VehicleData vehicle = this.player.vehicleData;
        vehicle.box = actor.box();
        vehicle.pitch = actor.pitch();
        vehicle.yaw = actor.yaw();
        vehicle.yawVelocity = actor.angularVelocity();
        vehicle.onGround = actor.onGround();
    }

    private void projectRider(final Vec3 actorPosition, final float actorYaw) {
        final Vec3 actor = new Vec3(actorPosition.x, actorPosition.y + VehicleType.BOAT_OFFSET, actorPosition.z);
        final Vec3 seat = MathUtil.seatPosition(actor, actorYaw, this.player.seatOffset);
        this.player.setPos(seat.down(this.player.getYOffset()));
        this.player.velocity = Vec3.ZERO.clone();
        this.player.getMovementTrace().log("boat: rider-format validation seat=" + this.player.position
                + ", retained actor=" + this.player.vehicleData.boatActorState.position());
    }
}
