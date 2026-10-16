package ac.boar.anticheat.compensated.cache.entity;

import ac.boar.anticheat.collision.DepenetrationState;
import ac.boar.anticheat.compensated.cache.entity.state.CachedEntityState;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.HorseGround;
import ac.boar.anticheat.player.data.HorseImmobility;
import ac.boar.anticheat.player.data.HorseJumpEffect;
import ac.boar.anticheat.player.data.VehicleType;
import ac.boar.anticheat.prediction.vehicle.FollowerHistory;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.anticheat.util.reach.PositionInterpolator;
import ac.boar.mappings.entity.EntityDefinition;
import ac.boar.mappings.entity.EntityType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ToString
@RequiredArgsConstructor
@Getter
@Setter
public final class EntityCache {
    private final BoarPlayer player;
    private final EntityType type;
    private final EntityDefinition definition;
    private final long runtimeId;
    private long uniqueId;
    // Set when we sent the removal packet. We still wait for its ack.
    private boolean removalSent;
    private final HorseJumpEffect horseJumpEffect = new HorseJumpEffect();
    private HorseImmobility horseImmobility = new HorseImmobility();
    private final HorseGround horseGround = new HorseGround(this);
    private Vec3 acknowledgedMotion;
    private long acknowledgedMotionTick;

    private EntityDimensions dimensions;
    private final DepenetrationState depenetrationState = new DepenetrationState();
    private Vec3 serverPosition = Vec3.ZERO;
    private Vec3 acknowledgedPositionTarget = Vec3.ZERO;
    // Last boat velocity we know for sure. Zero (a safe default) until we see any motion.
    private Vec3 boatVelocity = Vec3.ZERO;
    // A new rider link overwrites this value. A repeated link leaves it alone.
    private float boatAngularVelocity;
    private boolean inVehicle;
    private float pitch, yaw, headYaw;

    private EntityDataMap metadata = new EntityDataMap();
    private BoatBuoyancy boatBuoyancy;
    // The latest position and angles the server sent, in wire coordinates. A delta move only changes some of them.
    // Only set for actors that keep a history (ServerEntityPackets.keepsHistory).
    private float wireX, wireY, wireZ, wirePitch, wireYaw, wireHeadYaw;
    // What the client shows for a vehicle the server moves. Only set for entities that can be ridden.
    private FollowerHistory followerHistory;
    private long followerFallbackLogTick = Long.MIN_VALUE;
    private float actorPitch, actorYaw, actorHeadYaw;
    private boolean actorOnGround;
    // The angles of the last server move the client still steps toward (tickFollowerExternalRotation). Null when there is none.
    private ServerAngles pendingServerAngles;
    private FollowerHistory.ActorPose followerTickStart;
    // One interpolation step (smooth move toward the server position) of the boat the player drives.
    // BoarPlayer.tick records it when steps were left. BoatTicker uses it once, on this input only.
    private ControlledBoatStep controlledBoatStep;
    private BoatControlStart boatControlStart;
    private BoatControlStartPose boatControlStartPose;
    private DriverSwitchState driverSwitchState;
    private FollowerHistory.ActorPose switchBefore;
    private Vec3 switchVelocity;
    private VehicleType vehicleType;
    // The riders in the order the client keeps them (Actor::addPassenger).
    private final List<Passenger> passengers = new ArrayList<>();
    // Attribute values by name, like minecraft:movement. Only what the server sent.
    private final Map<String, Float> attributes = new HashMap<>();

    public record ControlledBoatStep(long inputTick, Vec3 position, Box box, float pitch, float yaw) {}

    public record ServerAngles(float pitch, float yaw, float headYaw) {}

    private record BoatControlStart(long inputTick, Vec3 position, Box box, EntityDimensions dimensions, Vec3 velocity,
                                    FollowerHistory.ActorPose pose) {}

    public record BoatControlStartPose(long inputTick, Vec3 position, Box box, Vec3 velocity, float pitch, float yaw, float angularVelocity,
                                       boolean onGround, FollowerHistory.ActorPose before) {}

    public record DriverSwitchState(long inputTick, FollowerHistory.ActorPose before, FollowerHistory.ActorPose after, Vec3 velocity) {}

    public record Passenger(long riderUniqueId, boolean isPlayer) {}

    // Boat physics uses the rider in the steering seat. Packet checks use the first passenger instead.
    public boolean isLocallySimulatedBoat() {
        final var vehicle = this.player.vehicleData;
        return this.getVehicleType() == VehicleType.BOAT && vehicle != null && vehicle.isMountedEntity(this) && this.isControlledBy(this.player.uniqueEntityId);
    }

    public ControlledBoatStep takeControlledBoatStep() {
        final ControlledBoatStep step = this.controlledBoatStep;
        this.controlledBoatStep = null;
        // Only a step from this input tick counts. A kept entity can miss the entity tick loop while chunks or entities are unavailable.
        return step != null && step.inputTick() == this.player.tick ? step : null;
    }

    public void prepareBoatControlTick() {
        this.boatControlStartPose = null;
        this.driverSwitchState = null;
        this.boatControlStart = new BoatControlStart(this.player.tick, this.current.getPos().clone(), this.current.getBoundingBox(), this.dimensions,
                this.boatVelocity.clone(), this.actorPose(this.current.getPos()));
    }

    private void ensureBoatControlTick() {
        if (this.boatControlStart == null || this.boatControlStart.inputTick() != this.player.tick) {
            // A kept actor that is not in the entity map still needs the normal entity tick, once.
            this.prepareBoatControlTick();
            this.followerTickStart = this.boatControlStart.pose();
            this.tickCurrent();
        }
    }

    // The normal entity tick: one step of the reach interpolator (`current`), then the same step for the actor rotation (tickFollowerExternalRotation).
    // Returns the interpolation steps that were left before this tick.
    public int tickCurrent() {
        final int steps = this.current.getInterpolator().getStep();
        this.current.tick();
        this.tickFollowerExternalRotation(steps);
        return steps;
    }

    public void beginDriverSwitchTick(final FollowerHistory history) {
        this.ensureBoatControlTick();
        final boolean historySource = history != null && !history.isLocallyPredicted();
        this.switchBefore = historySource ? history.currentPose(this.actorHeadYaw, this.actorOnGround) : this.boatControlStart.pose();
        this.switchVelocity = historySource
                ? (history.hasPositionStep() ? Vec3.ZERO.clone() : this.boatControlStart.velocity().clone())
                : this.boatVelocity.clone();
        this.controlledBoatStep = null;
        this.boatControlStartPose = null;
    }

    public void finishDriverSwitchTick(final FollowerHistory history) {
        final var after = history == null ? this.actorPose(this.current.getPos()) : history.currentPose(this.actorHeadYaw, this.actorOnGround);
        this.boatVelocity = this.switchVelocity.clone();
        this.driverSwitchState = new DriverSwitchState(this.player.tick, this.switchBefore, after, this.switchVelocity.clone());
    }

    // Call this before FollowerHistory.resetForLocalControl removes the history's active target. New queued moves are not picked here.
    public void captureBoatControlStartPose(final FollowerHistory history) {
        this.ensureBoatControlTick();
        final var ride = this.player.vehicleData;
        if (ride != null && ride.boatTakeover != null) {
            ride.boatTakeover.markHistoryStart(this.player, this, history);
        }
        // ensureBoatControlTick left a start for this input tick.
        final BoatControlStart start = this.boatControlStart;
        final boolean historySource = history != null && !history.isLocallyPredicted();
        final FollowerHistory.ActorPose before;
        final FollowerHistory.ActorPose actor;
        final Vec3 position;
        final Vec3 velocity;
        final Box box;
        if (historySource) {
            final FollowerHistory.ControlAdvance advance = history.advanceForControl(this.actorHeadYaw, this.actorOnGround);
            before = advance.before();
            actor = advance.after();
            position = new Vec3(actor.x(), actor.y() - this.getYOffset(), actor.z());
            velocity = advance.stepped() ? Vec3.ZERO.clone() : start.velocity().clone();
            // Same position: reuse the box saved at the start of this input. `current` (moved by the reach interpolator)
            // may have ticked already on this input, so its own box can be somewhere else.
            box = samePosition(start.position(), position) && this.dimensions.equals(start.dimensions()) ? start.box() : this.dimensions.getBoxAt(position);
        } else {
            position = this.current.getPos().clone();
            actor = this.actorPose(position);
            before = start.pose();
            velocity = this.boatVelocity.clone();
            box = this.current.getBoundingBox();
        }
        this.current.setBoatControlPose(position, box);
        this.actorPitch = actor.pitch();
        this.actorYaw = actor.yaw();
        this.boatVelocity = velocity.clone();
        // This start pose already includes the interpolation. Clear controlledBoatStep so boat physics does not add a step on top.
        this.controlledBoatStep = null;
        this.boatControlStartPose = new BoatControlStartPose(this.player.tick, position, box, velocity, actor.pitch(), actor.yaw(), this.boatAngularVelocity,
                actor.onGround(), before);
    }

    // Hands the boat from follower history to local control: the history restarts at the actor pose,
    // and the interpolation and the pending server angles are cleared. Call it after captureBoatControlStartPose.
    public void resetToLocalControl(final FollowerHistory history) {
        if (history != null) {
            final FollowerHistory.ActorPose actor = this.actorPose(this.current.getPos());
            history.resetForLocalControl(this.player.inputArrivalNs, this.player.getLatencyUtil().nowLowerBoundNs(), this.player.tick,
                    actor.x(), actor.y(), actor.z(), actor.pitch(), actor.yaw());
        }
        this.current.getInterpolator().clear();
        this.pendingServerAngles = null;
    }

    public BoatControlStartPose takeBoatControlStartPose() {
        final BoatControlStartPose seed = this.boatControlStartPose;
        this.boatControlStartPose = null;
        return seed != null && seed.inputTick() == this.player.tick ? seed : null;
    }

    private static boolean samePosition(final Vec3 first, final Vec3 second) {
        return first.x == second.x && first.y == second.y && first.z == second.z;
    }

    public FollowerHistory.ActorPose actorPose(final Vec3 position) {
        return new FollowerHistory.ActorPose(position.x, position.y + this.getYOffset(), position.z, this.actorPitch, this.actorYaw, this.actorHeadYaw,
                this.actorOnGround);
    }

    public void tickFollowerExternalRotation(final int stepsBeforeTick) {
        if (this.pendingServerAngles == null || stepsBeforeTick <= 0) {
            return;
        }
        this.actorPitch = FollowerHistory.stepAngle(this.actorPitch, this.pendingServerAngles.pitch(), stepsBeforeTick);
        this.actorYaw = FollowerHistory.stepAngle(this.actorYaw, this.pendingServerAngles.yaw(), stepsBeforeTick);
        if (stepsBeforeTick == 1) {
            this.pendingServerAngles = null;
        }
    }

    // A player that joins goes in front when the front rider is not a player, anyone else goes to the back.
    // Returns false when the rider is already on (nothing changes), true when it was added.
    public boolean addPassenger(final long riderUniqueId, final boolean isPlayer) {
        for (final Passenger passenger : this.passengers) {
            if (passenger.riderUniqueId() == riderUniqueId) {
                return false;
            }
        }

        final Passenger passenger = new Passenger(riderUniqueId, isPlayer);
        if (isPlayer && !this.passengers.isEmpty() && !this.passengers.get(0).isPlayer()) {
            this.passengers.add(0, passenger);
        } else {
            this.passengers.add(passenger);
        }
        return true;
    }

    public void removePassenger(final long uniqueId) {
        this.passengers.removeIf(passenger -> passenger.riderUniqueId() == uniqueId);
    }

    public boolean isControlledBy(final long uniqueId) {
        final Byte seat = this.metadata.get(EntityDataTypes.CONTROLLING_RIDER_SEAT_INDEX);
        final int index = seat == null ? 0 : seat;
        return index >= 0 && index < this.passengers.size() && this.passengers.get(index).riderUniqueId() == uniqueId;
    }

    // True only when the flag is set to true. Missing flags count as false.
    public boolean hasFlag(final EntityFlag flag) {
        return this.metadata.getFlags() != null && Boolean.TRUE.equals(this.metadata.getFlags().get(flag));
    }

    // A tamed horse with a saddle: the rider can steer it.
    public boolean isTamedAndSaddled() {
        return this.hasFlag(EntityFlag.TAMED) && this.hasFlag(EntityFlag.SADDLED);
    }

    // The client's movement ack checks use the first passenger, not the steering seat.
    public boolean hasFirstPlayerPassenger(final long uniqueId) {
        return !this.passengers.isEmpty() && this.passengers.get(0).isPlayer() && this.passengers.get(0).riderUniqueId() == uniqueId;
    }

    // Worked out once. Every move packet checks it.
    public VehicleType getVehicleType() {
        if (this.vehicleType == null) {
            this.vehicleType = VehicleType.fromIdentifier(this.definition.identifier());
        }
        return this.vehicleType;
    }

    public void setAttributes(final List<AttributeData> attributes) {
        for (final AttributeData data : attributes) {
            this.attributes.put(data.getName(), data.getValue());
        }
    }

    public float getAttribute(final String name, final float fallback) {
        return this.attributes.getOrDefault(name, fallback);
    }

    public void setMetadata(EntityDataMap metadata) {
        if (this.getVehicleType() == VehicleType.BOAT) {
            this.getBoatBuoyancy().update(metadata);
        }
        // An update holds only the flag words that changed, but putAll replaces all flags with them. So merge the new flags into
        // the old ones (an explicit collidable flag must survive an update of the first word). The packet map is not changed.
        final EnumMap<EntityFlag, Boolean> previous = this.metadata.getFlags();
        final EnumMap<EntityFlag, Boolean> update = metadata.getFlags();
        this.metadata.putAll(metadata);
        if (update != null) {
            final EnumMap<EntityFlag, Boolean> merged = new EnumMap<>(previous != null ? previous : update);
            merged.putAll(update);
            this.metadata.putFlags(merged);
        }

        final boolean hasWidth = this.metadata.containsKey(EntityDataTypes.WIDTH);
        final boolean hasHeight = this.metadata.containsKey(EntityDataTypes.HEIGHT);
        float width = hasWidth ? this.metadata.get(EntityDataTypes.WIDTH) : this.definition.width();
        float height = hasHeight ? this.metadata.get(EntityDataTypes.HEIGHT) : this.definition.height();

        // The client uses the size from the metadata. Without it, use the boat size Geyser uses.
        if (this.getVehicleType() == VehicleType.BOAT) {
            if (!hasWidth) {
                width = 1.6F;
            }
            if (!hasHeight) {
                height = 0.575F;
            }
        }

        float scale = this.metadata.containsKey(EntityDataTypes.SCALE)
                ? this.metadata.get(EntityDataTypes.SCALE)
                : 1.0F;
        this.setDimensions(EntityDimensions.fixed(width * scale, height * scale));
    }

    public void setDimensions(final EntityDimensions dimensions) {
        if (this.dimensions != null && (this.dimensions.width() != dimensions.width() || this.dimensions.height() != dimensions.height())) {
            this.depenetrationState.onSizeChanged();
        }
        this.dimensions = dimensions;
    }

    public BoatBuoyancy getBoatBuoyancy() {
        if (this.boatBuoyancy == null) {
            this.boatBuoyancy = new BoatBuoyancy();
        }
        return this.boatBuoyancy;
    }

    private CachedEntityState current;

    public boolean affectedByOffset;
    public float getYOffset() {
        if (this.affectedByOffset) {
            return this.getVehicleType() == VehicleType.BOAT ? VehicleType.BOAT_OFFSET : this.definition.offset();
        }

        return 0;
    }

    public void init() {
        this.current = new CachedEntityState(this.player, this);
        this.acknowledgedPositionTarget = this.serverPosition.clone();
    }

    public void applyRotation(Float pitch, Float yaw, Float headYaw) {
        if (pitch != null) this.pitch = pitch;
        if (yaw != null) this.yaw = yaw;
        if (headYaw != null) this.headYaw = headYaw;
    }

    public void interpolate(Vec3 pos, boolean lerp) {
        this.interpolate(pos.x, pos.y, pos.z, lerp, true);
    }

    // serverPosition moves when we send the packet. The interpolation target moves only when the ack arrives.
    public Vec3 getAcknowledgedPositionTarget() {
        return this.acknowledgedPositionTarget.clone();
    }

    public void interpolate(Float posX, Float posY, Float posZ, boolean lerp, boolean applyCurrent) {
        // Keep packet order without using the reach interpolator. Its old target can survive a teleport.
        final Vec3 acknowledged = this.acknowledgedPositionTarget.clone();
        if (posX != null) {
            acknowledged.x = posX;
        }
        if (posY != null) {
            acknowledged.y = posY;
        }
        if (posZ != null) {
            acknowledged.z = posZ;
        }
        this.acknowledgedPositionTarget = acknowledged;
        if (!applyCurrent) {
            return;
        }
        final PositionInterpolator lv = this.current.getInterpolator();
        // A delta packet leaves out some axes. They keep the last server value we received, even when the boat is predicted locally.
        Vec3 pos = this.current.isLocallySimulatedPose()
                ? acknowledged.clone()
                : (lv.getTargetPos() == null ? this.current.getPos() : lv.getTargetPos()).clone();
        if (posX != null) pos.x = posX;
        if (posY != null) pos.y = posY;
        if (posZ != null) pos.z = posZ;

        if (!lerp) {
            this.current.setTeleportPos(pos);
        } else if (lv != null) {
            lv.refreshPositionAndAngles(pos);
        }
    }

}
