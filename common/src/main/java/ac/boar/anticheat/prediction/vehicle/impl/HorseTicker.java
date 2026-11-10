package ac.boar.anticheat.prediction.vehicle.impl;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.collision.Collider;
import ac.boar.anticheat.collision.MovementCollision;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.HorseGround;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.prediction.ticker.base.EntityTicker;
import ac.boar.anticheat.prediction.vehicle.HorseJumpBlocks;
import ac.boar.anticheat.prediction.vehicle.HorseJumpPhysics;
import ac.boar.anticheat.prediction.vehicle.PositionSet;
import ac.boar.anticheat.prediction.vehicle.VehicleTicker;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.block.Blocks;
import ac.boar.mappings.block.Properties;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

// A tamed, saddled horse that the client steers.
// Client step order: liquid push, DesiredMoveDirectionSystem, GroundTravelTypeSystemImpl (or WaterTravelSystem in water),
// DefaultMoveSystems::horizontalMovement, the move (with a block slowdown and the auto step), water drag or gravity and drag, friction,
// climb out of liquid, then the blocks the horse is inside.
// If we cannot tell how strong a charged jump was, we try each possible launch and keep the closest. The normal collision and offset checks still run.
// Not simulated: a horse whose minecraft:movement attribute is missing (see notSimulated).
// Only VehicleTicker.forVehicle builds this ticker, and only when clientSimulates is true. So for the whole tick(): the rider is the driver,
// the horse entity is cached (never null), and it is TAMED and SADDLED. VehiclePredictionRunner.run also sets needsVehicleRestart to false
// before tick() when the entity is cached. Nothing in tick() changes these.
public class HorseTicker extends VehicleTicker {

    private static final float STRAFE_SCALE = 0.5F;
    private static final float BACKWARD_SCALE = 0.25F;
    private static final float GROUND_DRAG = 0.54600006F;
    private static final float FRICTION_SCALE = 0.91F;
    private static final float AIR_SPEED_SCALE = 0.1F;
    private static final float MIN_INPUT_SQR = 1.0E-4F;
    private static final float GRAVITY = 0.08F;
    private static final float VERTICAL_DRAG = 0.98F;
    // VariableMaxAutoStepSystemImpl::tickEntity (0x105acee04), values from the Horse constructor (0x108afe95c):
    // a horse steered by its rider (WASD_CONTROLLED) steps up 1.0625. Without that, or on a jump-preventing block (honey, same probe as the jump), 0.5625.
    private static final float STEP_HEIGHT_CONTROLLED = 1.0625F;
    private static final float STEP_HEIGHT_BASE = 0.5625F;
    // GroundTravelTypeSystemImpl::_calcGroundFrictionV2 (0x1051d4d54): soul sand (block name test) without Soul Speed raises only the friction used for
    // acceleration. The friction that slows the horse after the move does not change. A horse has no boots, so it never has Soul Speed.
    private static final float SOUL_SAND_FRICTION_SCALE = 1.225F;
    // MobMovementDrag::tickApplyWaterDrag (0x105b65d64): horizontal 0.8 (0.9 with the SPRINTING flag), vertical 0.8, no epsilon.
    // Then gravity -0.02 (0x105b3fef8).
    private static final float WATER_DRAG = 0.8F;
    private static final float WATER_SPRINT_DRAG = 0.9F;
    private static final float WATER_GRAVITY = 0.02F;
    private static final float WATER_SPEED_DEFAULT = 0.02F;
    // ClimbOutOfLiquid (0x105b3cd8c). The rule is at its use in applySimulation.
    private static final float CLIMB_OUT_VELOCITY = 0.3F;
    private static final float CLIMB_OUT_LIFT = 0.6F;
    private static final float GROUND_PROBE = 1.0E-3F;
    // The bounds of the mount tick start velocity (mountStartVelocity).
    private static final float MOUNT_START_SLACK = 0.1F;
    private static final float MOUNT_START_MAX_HORIZONTAL = 2.0F;
    private static final float MOUNT_START_MAX_VERTICAL = 4.0F;

    // State for one tick() call. VehiclePredictionRunner.run builds a new ticker each tick and calls tick() once, so no field needs a reset.
    private MountTrial mountTrial;
    // For a grounded horse with the NO_AI flag (see noAiMode): which client outcomes we try. NORMAL: travel only. IMMOBILE: the horse stays still.
    // INPUT_AND_IMMOBILE: travel with the rider input, or still. ALL_AND_IMMOBILE: travel with or without the input, or still.
    private enum NoAiMode { NORMAL, IMMOBILE, INPUT_AND_IMMOBILE, ALL_AND_IMMOBILE }
    private NoAiMode noAiMode = NoAiMode.NORMAL;
    private Immobile immobileTrial;
    private float travelYaw, travelPitch;
    private boolean invalidateJumpOnTravel;
    // The client's WasInWaterFlag for this tick: the box at the start of the tick touches water (EntityTicker.touchesWater). Water travel then runs.
    private boolean inWater;
    private boolean matchedClaimEligible;
    // HorseJumpBlocks.preventsJump at this tick's start box. Every trial starts from that box. null when a block is unknown.
    private Boolean jumpPrevented;
    private record MountTrial(HorseGround.State before, Simulation air) {}

    public HorseTicker(BoarPlayer player, VehicleData vehicle) {
        super(player, vehicle);
    }

    @Override
    public void tick() {
        // Read before travelTick clears the mount window. No ticks or needsVehicleRestart test is needed. needsVehicleRestart is false here
        // (see the class comment), and the mount tick (ticks == 0) returns in travelTick before commitOutcome.
        this.matchedClaimEligible = this.vehicle.horseMountWindow == VehicleData.HorseMountWindow.NONE;
        final EntityCache cache = this.player.compensatedWorld.getEntity(this.vehicle.vehicleRuntimeId);
        if (cache.getHorseGround().state() == HorseGround.State.UNKNOWN) {
            this.vehicle.invalidateHorseJump();
        }
        if (cache.getAttributes().get("minecraft:movement") == null) {
            VehicleTicker.stopHorseTravel(this.player, this.vehicle, "not simulated", cache);
            this.vehicle.slowdown = null;
            this.vehicle.invalidateHorseJump();
            this.notSimulated(cache);
            return;
        }

        travelTick(cache);
    }

    // One tick of horse travel. The class comment says how an unknown jump is handled.
    private void travelTick(final EntityCache cache) {
        if (!this.player.compensatedWorld.isChunkLoadedAt(this.player.position.x, this.player.position.z)) {
            VehicleTicker.stopHorseTravel(this.player, this.vehicle, "unloaded chunk", cache);
            this.vehicle.slowdown = null;
            this.player.getMovementTrace().log("horse: unloaded chunk, held still");
            this.player.velocity = Vec3.ZERO.clone();
            if (this.vehicle.horsePendingScale > 0) {
                this.vehicle.invalidateHorseJump();
            }
            return;
        }
        if (this.vehicle.ticks == 0) {
            VehicleTicker.stopHorseTravel(this.player, this.vehicle, "mount tick", null);
            this.vehicle.slowdown = null;
            this.vehicle.invalidateHorseJump();
            if (this.vehicle.horseMountWindow != VehicleData.HorseMountWindow.RELEASED) {
                this.vehicle.horseMountWindow = VehicleData.HorseMountWindow.PENDING;
            }
            mount(cache);
            return;
        }

        consumeForcedMove(cache);
        final var mountWindow = this.vehicle.horseMountWindow;
        final Vec3 mountStart = this.vehicle.horseMountStart;
        this.vehicle.horseMountWindow = VehicleData.HorseMountWindow.NONE;
        this.vehicle.horseMountStart = null;
        // Try the mount-tick start (mountTrial) only if no server motion changed the velocity since mount() set it.
        final boolean mountAlternatives = mountWindow == VehicleData.HorseMountWindow.PENDING && mountStart != null
                && this.player.velocity.x == mountStart.x && this.player.velocity.y == mountStart.y && this.player.velocity.z == mountStart.z;
        if (mountWindow != VehicleData.HorseMountWindow.NONE && !mountAlternatives) {
            cache.getHorseGround().markUnknown();
        }

        final Box box = this.vehicle.boxAt(this.player.position, cache.getDimensions());
        this.jumpPrevented = HorseJumpBlocks.preventsJump(this.player, box);
        final EntityTicker fluids = new EntityTicker(this.player);
        // Lava travel is not modeled: a horse in lava only gets the land path and the lava push.
        this.inWater = fluids.touchesWater(box);
        // LiquidPhysicsSystem::_liquidBlockFetch (0x104aa22d8) pushes the horse along flowing water or lava before the travel systems,
        // like a player or a driven boat. The push needs ControlledByLocalInstance, which a steered horse has.
        // Not checked: whether the very first steered tick gets the push (a driven boat does not).
        final Vec3 push = fluids.fluidPushFor(box);
        if (push.lengthSquared() > 0) {
            this.player.velocity = this.player.velocity.add(push);
            this.player.getMovementTrace().log("horse: liquid push " + push);
        }
        this.travelYaw = HorseJumpPhysics.turn(this.vehicle.yaw, this.player.yaw);
        this.travelPitch = MathUtil.wrapYaw(this.player.pitch * 0.5F);
        final HorseGround.State mountGround = cache.getHorseGround().state();
        if (mountAlternatives && mountGround != HorseGround.State.TRUE) {
            this.mountTrial = new MountTrial(mountGround, predictSimulation(cache, this.player.velocity, box, false, false));
        }
        final boolean inputKnown = this.player.horseJumpInput.known();
        final boolean unknownJump = !this.vehicle.horseJumpKnown || !inputKnown;
        final boolean release = this.vehicle.prevJumping && !this.vehicle.jumping;
        if (release && !inputKnown) {
            this.vehicle.invalidateHorseStand();
        }
        this.noAiMode = noAiMode(cache);
        if (this.noAiMode != NoAiMode.NORMAL) {
            this.immobileTrial = new Immobile();
        }
        final boolean controls = !this.vehicle.pendingLeave && this.vehicle.matchesMountedEntity(cache) && cache.isControlledBy(this.player.uniqueEntityId)
                && HorseJumpPhysics.hasControlFlag(cache) && cache.hasFlag(EntityFlag.CAN_POWER_JUMP)
                && cache.getAttributes().containsKey("minecraft:horse.jump_strength");
        final boolean unknownRelease = unknownJump && release && this.vehicle.onGround && !cache.hasFlag(EntityFlag.HAS_DASH_COOLDOWN);
        final boolean knownLaunch = !unknownJump && this.vehicle.horsePendingScale > 0 && cache.getHorseGround().state() == HorseGround.State.TRUE
                && !this.vehicle.horseJumping;
        if (controls && (unknownRelease || knownLaunch && this.noAiMode != NoAiMode.IMMOBILE)) {
            final float factor = HorseJumpBlocks.factor(this.jumpPrevented);
            final float strength = cache.getAttribute("minecraft:horse.jump_strength", Float.NaN);
            final var effect = cache.getHorseJumpEffect();
            final boolean effectKnown = effect.known();
            final float boost = effectKnown ? effect.boost() : effect.uncertainActiveBoost();
            final boolean uncertainMaterial = Float.isNaN(factor);
            // factor is 1.0, 0.6 or NaN (unknown block, uncertainMaterial), so it needs no finite test (HorseJumpBlocks.factor).
            if (Float.isFinite(strength) && Float.isFinite(boost) && Float.isFinite(cache.getAttribute("minecraft:movement", Float.NaN))) {
                if (knownLaunch && effectKnown) {
                    final float pending = this.vehicle.horsePendingScale;
                    final float launchFactor = uncertainMaterial ? 1.0F : factor;
                    final Vec3 launch = HorseJumpPhysics.launch(this.player.velocity, strength, pending, boost, launchFactor, this.travelYaw,
                            this.vehicle.motion.getY());
                    // The client's ResetMoveDirection runs before ApplyJump uses up the pending jump power, so it still sees the power above 0.
                    // So the probe keeps the rider input, the same as predictTravelWithoutLaunch while jump power is pending.
                    Simulation probe = predictSimulation(cache, launch, box);
                    if (uncertainMaterial) {
                        final Vec3 reducedLaunch = HorseJumpPhysics.launch(this.player.velocity, strength, pending, boost, 0.6F, this.travelYaw,
                                this.vehicle.motion.getY());
                        final Simulation reducedProbe = predictSimulation(cache, reducedLaunch, box);
                        // On an exact tie the normal factor wins. Both probes start from the same state.
                        if (distanceSquared(reducedProbe) < distanceSquared(probe)) {
                            probe = reducedProbe;
                        }
                    }
                    final boolean immobileCloser = this.immobileTrial != null && distanceSquared(this.immobileTrial) < distanceSquared(probe);
                    final Outcome winner = chooseMountStart(immobileCloser ? this.immobileTrial : probe);
                    // No velocity write here: applySimulation sets it from the probe's move, which starts from the launch.
                    if (winner == probe) {
                        this.vehicle.horsePendingScale = 0;
                        this.vehicle.horseJumping = true;
                    }
                    commitOutcome(cache, winner);
                } else {
                    selectJump(cache, box, strength, factor, uncertainMaterial, boost, !effectKnown, inputKnown);
                }
                return;
            }
            this.invalidateJumpOnTravel = true;
            this.player.getMovementTrace().log("horse: jump strength, jump boost or speed is not finite, simulated without launch");
        }
        simulate(cache, box);
    }

    private void consumeForcedMove(final EntityCache cache) {
        final var move = this.vehicle.horseForcedMove;
        if (move == null) {
            return;
        }
        // clientSimulates is true for the whole tick (see the class comment).
        if (!this.vehicle.matchesForcedHorseEntity(cache) || this.vehicle.horseForcedCompletionSeen
                || !cache.hasFirstPlayerPassenger(this.player.uniqueEntityId)) {
            VehicleTicker.dropHorseForcedMove(this.player, this.vehicle, "ride or control changed");
            return;
        }
        this.vehicle.horseForcedMove = null;
        // Same math order as PositionInterpolator: reciprocal, multiply, add. This moves one third of the way to the target.
        // Local prediction clears the other two steps.
        final float reciprocal = 1.0F / 3;
        final Vec3 start = this.player.position, target = move.position();
        this.player.setPos(new Vec3(start.x + (target.x - start.x) * reciprocal, start.y + (target.y - start.y) * reciprocal,
                start.z + (target.z - start.z) * reciprocal));
        this.player.velocity = Vec3.ZERO.clone();
        this.vehicle.yaw = MathUtil.wrapYaw(this.vehicle.yaw + MathUtil.wrapYaw(move.yaw() - this.vehicle.yaw) * reciprocal);
        this.vehicle.onGround = move.onGround();
        this.vehicle.horseGroundFromTravel = false;
        this.player.getMovementTrace().log("horse forced move: consumed target=" + target + " position=" + this.player.position
                + " yaw=" + this.vehicle.yaw + " onGround=" + this.vehicle.onGround);
    }

    private void selectJump(final EntityCache cache, final Box box, final float strength, final float factor, final boolean uncertainMaterial,
                            final float activeBoost, final boolean uncertainBoost, final boolean inputKnown) {
        final Vec3 initialVelocity = this.player.velocity;
        // Always pick a real prediction. This includes the case where a finite end point gives a score too big for a float.
        Outcome winner = predictWithoutLaunch(cache, initialVelocity, box);
        float bestDistanceSquared = distanceSquared(winner);
        int bestRequest = -1;
        float bestScale = 0;
        float bestBoost = 0;
        float bestFactor = 0;
        final int charges = inputKnown ? (this.vehicle.horsePendingScale > 0 ? 1 : 0) : 91;
        final int boosts = uncertainBoost && activeBoost != 0 ? 2 : 1;
        final int factors = uncertainMaterial ? 2 : 1;
        for (int index = 0; index < charges * boosts * factors; index++) {
            final int request = index / (boosts * factors);
            final float scale = inputKnown ? this.vehicle.horsePendingScale : HorseJumpPhysics.scale(request);
            final float boost = index / factors % boosts == 0 ? activeBoost : 0;
            final float candidateFactor = uncertainMaterial ? (index % factors == 0 ? 1.0F : 0.6F) : factor;
            final Vec3 velocity = HorseJumpPhysics.launch(initialVelocity, strength, scale, boost, candidateFactor, this.travelYaw, this.vehicle.motion.getY());
            final Simulation candidate = predictSimulation(cache, velocity, box);
            final float distanceSquared = distanceSquared(candidate);
            final boolean wins = distanceSquared < bestDistanceSquared || Float.isFinite(distanceSquared) && distanceSquared == bestDistanceSquared
                    && (bestRequest < 0 || request < bestRequest
                    || request == bestRequest && (boost < bestBoost || boost == bestBoost && candidateFactor > bestFactor));
            if (wins) {
                winner = candidate;
                bestDistanceSquared = distanceSquared;
                bestRequest = request;
                bestScale = scale;
                bestBoost = boost;
                bestFactor = candidateFactor;
            }
        }
        final Outcome selected = chooseMountStart(winner);
        if (selected != winner) {
            winner = selected;
            bestDistanceSquared = distanceSquared(winner);
            bestRequest = -1;
            bestScale = bestBoost = bestFactor = 0;
        } else if (!(winner instanceof Immobile)) {
            this.vehicle.horseJumpKnown = true;
        }
        if (bestRequest >= 0) {
            this.vehicle.horsePendingScale = 0;
            this.vehicle.horseJumping = true;
        }
        // If no launch was chosen, the pending jump power and MobIsJumping stay set until the normal landing code clears them.
        commitOutcome(cache, winner);
        this.player.getMovementTrace().log("horse jump search: selected request=" + bestRequest + " jumpScale=" + bestScale + " inputKnown=" + inputKnown
                + " boost=" + bestBoost + " distanceSquared=" + bestDistanceSquared + " horseKnownAfterCommit=" + this.vehicle.horseJumpKnown
                + (uncertainMaterial ? " factor=" + bestFactor : ""));
    }

    // Without the horse's speed (minecraft:movement), we cannot simulate it. We hold it still and the offset check corrects the client.
    // The yaw, pitch and ground state are copied so the next simulated tick starts right.
    // In-game testing never hit this: the speed always came before the mount. Not checked: Geyser or other servers.
    private void notSimulated(final EntityCache cache) {
        this.player.getMovementTrace().log("horse: not simulated, no minecraft:movement, held still");
        this.player.velocity = Vec3.ZERO.clone();
        this.vehicle.yaw = this.vehicle.vehicleRotation.getY();
        this.vehicle.pitch = this.vehicle.vehicleRotation.getX();
        this.vehicle.onGround = this.isOnGround(cache);
    }

    // The client starts from the horse it shows, then moves one tick. We use the client position only if mountNear accepts it.
    // Otherwise we use the tracked horse.
    private void mount(final EntityCache cache) {
        final Vec3 tracked = cache.getServerPosition();
        // The upward start velocity is capped at the horse's jump strength.
        // Not covered: Jump Boost (HorseJumpPhysics.launch adds it) or a bubble column can make the horse go up faster.
        final Vec3 firstMove = this.mountStartVelocity(cache, cache.getAttribute("minecraft:horse.jump_strength", Float.MAX_VALUE));
        final boolean near = this.mountNear(cache, tracked, firstMove);
        this.player.setPos(near ? this.player.unvalidatedPosition.clone() : tracked.clone());
        this.player.velocity = near ? firstMove : Vec3.ZERO.clone();

        // The horse may already turn on the mount tick, so the tracked yaw can be far off. The rider steers it anyway.
        this.vehicle.yaw = this.vehicle.vehicleRotation.getY();
        this.vehicle.pitch = this.vehicle.vehicleRotation.getX();
        this.vehicle.horseMountStart = null;
        if (near && this.vehicle.motionOverride == null) {
            final Vec3 claim = this.player.unvalidatedTickEnd;
            Vec3 seed = null;
            if (claim.x == 0 && claim.y == -0.0784F && claim.z == 0) {
                seed = new Vec3(0, -0.0784F, 0);
            } else if (this.vehicle.motion.getX() == 0 && this.vehicle.motion.getY() == 1) {
                // Measured on the mount tick with full forward input (motion 0, 1): the start velocity is the air acceleration
                // (speed * 0.1) times the mob drag 0.98, turned to the yaw, times 0.91. y is -0.0784.
                final float input = 0.98F * (cache.getAttribute("minecraft:movement", 0) * AIR_SPEED_SCALE);
                final float angle = this.vehicle.yaw * MathUtil.DEGREE_TO_RAD;
                final float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
                final Vec3 airSeed = new Vec3((0F - sin * input) * FRICTION_SCALE, -0.0784F, (cos * input + 0F) * FRICTION_SCALE);
                if (claim.x == airSeed.x && claim.y == airSeed.y && claim.z == airSeed.z) {
                    seed = airSeed;
                }
            }
            if (seed != null) {
                this.player.velocity = seed;
                this.vehicle.horseMountStart = seed.clone();
            }
        }
        this.vehicle.onGround = this.isOnGround(cache);
        this.player.getMovementTrace().log("horse: mount tick, tracked=" + tracked + " client=" + this.player.unvalidatedPosition + " near=" + near
                + " yaw=" + this.vehicle.yaw + " onGround=" + this.vehicle.onGround);
    }

    // The velocity a mount tick starts from.
    // We trust the client Delta only up to how far the tracked vehicle moved last tick, plus a little (MOUNT_START_SLACK).
    // Any leftover error is caught by the offset check on the next tick.
    // A value that is not finite counts as no velocity.
    private Vec3 mountStartVelocity(final EntityCache cache, final float maxUp) {
        if (this.vehicle.motionOverride != null) {
            return this.vehicle.motionOverride.clone();
        }
        final Vec3 client = this.player.unvalidatedTickEnd;
        if (cache.getCurrent() == null || !MathUtil.finite(client)) {
            return Vec3.ZERO.clone();
        }

        final Vec3 tracked = cache.getCurrent().getPos().subtract(cache.getCurrent().getPrevPos());
        final float horizontalBound = Math.min(MOUNT_START_MAX_HORIZONTAL, (float) Math.hypot(tracked.x, tracked.z) + MOUNT_START_SLACK);
        final float horizontal = (float) Math.hypot(client.x, client.z);
        final float scale = horizontal > horizontalBound ? horizontalBound / horizontal : 1.0F;

        final float upBound = Math.min(maxUp, Math.min(MOUNT_START_MAX_VERTICAL, Math.abs(tracked.y) + MOUNT_START_SLACK));
        final float downBound = Math.min(MOUNT_START_MAX_VERTICAL, Math.abs(tracked.y) + MOUNT_START_SLACK);
        final float y = Math.max(-downBound, Math.min(client.y, upBound));

        final Vec3 start = new Vec3(client.x * scale, y, client.z * scale);
        if (scale != 1.0F || y != client.y) {
            this.player.getMovementTrace().log("mount: client velocity " + client + " clamped to " + start + " (tracked move " + tracked + ")");
        }
        return start;
    }

    /**
     * Is the client's mount position one the client can really have?
     * The client shows the horse on its follower path, then moves it one tick. render = start of this tick, shown = after this tick's step.
     * So the claim must be inside one of the two FollowerEnvelope sets (built only from times Boar saw), plus at most the x/z length of firstMove
     * (the clamped start velocity, mountStartVelocity). Only the float-precision slack is added.
     * In-game testing showed mounts right after a long teleport stay inside the sets.
     * Without a trusted set for this tick (no history yet, the history did not tick, or no latency reply came back yet), we take the horse as
     * shown where the server has it. The claim must then be within the first move's x/z length of the tracked position.
     */
    private boolean mountNear(final EntityCache cache, final Vec3 tracked, final Vec3 firstMove) {
        final Vec3 claim = this.player.unvalidatedPosition;
        final float allowance = (float) Math.hypot(firstMove.x, firstMove.z);
        final var history = cache.getFollowerHistory();
        final var envelope = history == null ? null : history.envelope();
        if (history == null || !history.tickedAt(this.player.tick) || envelope.assumedNow()) {
            return claim.distanceTo(tracked) <= allowance + PositionSet.slack(claim.x, claim.y, claim.z);
        }
        final float x = claim.x, y = claim.y + cache.getYOffset(), z = claim.z;
        final float slack = PositionSet.slack(x, y, z);
        final float excess = Math.min(envelope.renderSet().excess(x, y, z, slack), envelope.shownSet().excess(x, y, z, slack));
        return excess <= allowance;
    }

    private void simulate(final EntityCache cache, final Box box) {
        commitOutcome(cache, chooseMountStart(predictWithoutLaunch(cache, this.player.velocity, box)));
    }

    private Outcome chooseMountStart(final Outcome probe) {
        if (this.mountTrial == null) {
            return probe;
        }
        final Simulation air = this.mountTrial.air();
        final float airDistanceSquared = distanceSquared(air);
        final float probeDistanceSquared = distanceSquared(probe);
        final boolean useAir = airDistanceSquared < probeDistanceSquared
                || airDistanceSquared == probeDistanceSquared && this.mountTrial.before() == HorseGround.State.FALSE;
        this.player.getMovementTrace().log("horse mount: before=" + this.mountTrial.before() + " air=" + useAir + " airDistanceSquared=" + airDistanceSquared
                + " probeDistanceSquared=" + probeDistanceSquared + " extraTrials=1");
        return useAir ? air : probe;
    }

    private NoAiMode noAiMode(final EntityCache cache) {
        final boolean provenGround = this.vehicle.onGround
                && (this.vehicle.horseGroundFromTravel || cache.getHorseGround().state() == HorseGround.State.TRUE || this.mountTrial != null);
        if (!cache.hasFlag(EntityFlag.NO_AI) || !provenGround) {
            return NoAiMode.NORMAL;
        }
        final var history = cache.getHorseImmobility();
        if (!history.slidingPossible() && !history.mobJumpingPossible()) {
            return NoAiMode.IMMOBILE;
        }
        if (Boolean.TRUE.equals(this.vehicle.horseStandSliding)) {
            return NoAiMode.NORMAL;
        }
        return history.mobJumpingPossible() ? NoAiMode.ALL_AND_IMMOBILE : NoAiMode.INPUT_AND_IMMOBILE;
    }

    private float distanceSquared(final Outcome prediction) {
        if (prediction instanceof Immobile) {
            return this.player.position.squaredDistanceTo(this.player.unvalidatedPosition);
        }
        return distanceSquared(((Simulation) prediction).collision().finalBox());
    }

    // Squared distance from the client's claimed position to the position of an end box.
    private float distanceSquared(final Box box) {
        return VehicleData.boxPosition(box).squaredDistanceTo(this.player.unvalidatedPosition);
    }

    private Outcome predictWithoutLaunch(final EntityCache cache, final Vec3 velocity, final Box box) {
        if (this.noAiMode == NoAiMode.IMMOBILE) {
            return this.immobileTrial;
        }
        final Simulation travel = this.noAiMode == NoAiMode.INPUT_AND_IMMOBILE
                ? predictSimulation(cache, velocity, box)
                : predictTravelWithoutLaunch(cache, velocity, box);
        if (this.noAiMode == NoAiMode.NORMAL) {
            return travel;
        }
        return distanceSquared(this.immobileTrial) <= distanceSquared(travel) ? this.immobileTrial : travel;
    }

    private Simulation predictTravelWithoutLaunch(final EntityCache cache, final Vec3 velocity, final Box box) {
        final boolean jumpKnown = this.vehicle.horseJumpKnown && !this.invalidateJumpOnTravel;
        if (!this.vehicle.onGround || !cache.hasFlag(EntityFlag.WASD_CONTROLLED)
                || Boolean.FALSE.equals(this.vehicle.horseStanding) || Boolean.TRUE.equals(this.vehicle.horseStandSliding)
                || jumpKnown && this.vehicle.horsePendingScale != 0) {
            return predictSimulation(cache, velocity, box);
        }
        if (jumpKnown && Boolean.TRUE.equals(this.vehicle.horseStanding) && Boolean.FALSE.equals(this.vehicle.horseStandSliding)) {
            return predictSimulation(cache, velocity, box, true);
        }
        // If the local state is unknown or jump power is pending, both client outcomes are possible: input used, or input zeroed.
        // Try both from the same start state.
        final Simulation input = predictSimulation(cache, velocity, box);
        final Simulation zero = predictSimulation(cache, velocity, box, true);
        final float inputDistanceSquared = distanceSquared(input);
        final float zeroDistanceSquared = distanceSquared(zero);
        return zeroDistanceSquared < inputDistanceSquared ? zero : input;
    }

    // Only computes. Changes no state. All trials start from the same state, so none may save ground, motion or contact history.
    private Simulation predictSimulation(final EntityCache cache, final Vec3 initialVelocity, final Box box) {
        return predictSimulation(cache, initialVelocity, box, false);
    }

    private Simulation predictSimulation(final EntityCache cache, final Vec3 initialVelocity, final Box box, final boolean zeroInput) {
        return predictSimulation(cache, initialVelocity, box, zeroInput, this.vehicle.onGround);
    }

    // The horse turns to travelYaw this tick (set in travelTick before any trial).
    private Simulation predictSimulation(final EntityCache cache, final Vec3 initialVelocity, final Box box, final boolean zeroInput, final boolean onGround) {
        final float speed = cache.getAttribute("minecraft:movement", 0);

        // The rider input is used as it is, without the 0.98 a mob gets.
        float x = zeroInput ? 0 : this.vehicle.motion.getX() * STRAFE_SCALE;
        float z = zeroInput ? 0 : this.vehicle.motion.getY() <= 0 ? this.vehicle.motion.getY() * BACKWARD_SCALE : this.vehicle.motion.getY();

        // GroundTravelTypeSystemImpl: the block under the horse at the start of the tick.
        final BoarBlockState below = onGround ? this.player.compensatedWorld.getBlockState(this.player.getBlockPosBelowThatAffectsMyMovement(), 0) : null;
        final float blockFriction = below != null ? below.getFriction() : 1.0F;
        // The friction that slows the horse after the move. Water: see WATER_DRAG. A horse has no boots, so no Depth Strider.
        // Off the ground, blockFriction is 1.0, so the land friction is FRICTION_SCALE.
        final float friction = this.inWater ? (cache.hasFlag(EntityFlag.SPRINTING) ? WATER_SPRINT_DRAG : WATER_DRAG) : blockFriction * FRICTION_SCALE;
        final float acceleration;
        if (this.inWater) {
            // WaterTravelSystem (0x105abc918) uses the underwater speed attribute. Mob::_initialize (0x1090ffafc) registers it for every mob at 0.02.
            // Geyser does not send it, and in-game testing never saw it sent, so a horse has 0.02 (WATER_SPEED_DEFAULT). A sent attribute wins.
            // In-game testing in still water (W held) showed the speed from rest grows as v = 0.8 * (v + 0.02) per tick.
            acceleration = cache.getAttribute("minecraft:underwater_movement", WATER_SPEED_DEFAULT);
        } else if (onGround) {
            // Soul sand: see SOUL_SAND_FRICTION_SCALE. A friction of zero uses the default drag instead, the same as the player.
            final float accelerationFriction = (below.is(Blocks.SOUL_SAND) ? blockFriction * SOUL_SAND_FRICTION_SCALE : blockFriction) * FRICTION_SCALE;
            final float ratio = GROUND_DRAG / (accelerationFriction == 0 ? GROUND_DRAG : accelerationFriction);
            acceleration = speed * (ratio * (ratio * ratio));
        } else {
            acceleration = speed * AIR_SPEED_SCALE;
        }

        // DefaultMoveSystems::horizontalMovement
        float vx = initialVelocity.x, vy = initialVelocity.y, vz = initialVelocity.z;
        final float inputLengthSquared = x * x + z * z;
        if (inputLengthSquared >= MIN_INPUT_SQR) {
            final float scale = acceleration / Math.max(1.0F, (float) Math.sqrt(inputLengthSquared));
            x *= scale;
            z *= scale;
            final float angle = this.travelYaw * MathUtil.DEGREE_TO_RAD;
            final float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
            vx += cos * x - sin * z;
            vz += cos * z + sin * x;
        }

        // BlockMovementSlowdownMultiplierSystem::applySlowdownOnMove (0x1050a710c): a slowdown from a block the horse was inside after its last move
        // scales this move per axis. The velocity is zero after the move (applySimulation). The trials only read it; applySimulation clears it.
        final Vec3 slowdown = this.vehicle.slowdown;
        Vec3 movement = new Vec3(vx, vy, vz);
        if (slowdown != null) {
            movement = movement.multiply(slowdown);
        }

        // Move and collide. An unknown block under the horse leaves the step height open: try both and keep the closer end box.
        final Boolean stepJumpPrevented = onGround ? this.jumpPrevented : Boolean.FALSE;
        final float[] stepHeights = !cache.hasFlag(EntityFlag.WASD_CONTROLLED) || Boolean.TRUE.equals(stepJumpPrevented) ? new float[] {STEP_HEIGHT_BASE}
                : stepJumpPrevented == null ? new float[] {STEP_HEIGHT_CONTROLLED, STEP_HEIGHT_BASE} : new float[] {STEP_HEIGHT_CONTROLLED};
        // Developer debug only: the jump search can run this hundreds of times in one tick.
        if (stepHeights.length > 1 && Boar.getInstance().getPlatform().developerDebug()) {
            this.player.getMovementTrace().log("horse: unknown block under the horse, trying both step heights (client-fitted pick)");
        }
        Collider.MoveResult result = null;
        float stepHeight = stepHeights[0];
        boolean collisionKnown;
        boolean clamped = movement.lengthSquared() > MovementCollision.MAX_MOVE_LENGTH_SQUARED;
        try (var reads = this.player.compensatedWorld.trackBlockReads()) {
            float distanceSquared = Float.POSITIVE_INFINITY;
            for (final float candidateStep : stepHeights) {
                final Collider.MoveResult candidateResult = Collider.collideBox(this.player, box, movement, candidateStep, onGround);
                final float candidateDistanceSquared = distanceSquared(candidateResult.finalBox());
                if (result == null || candidateDistanceSquared < distanceSquared) {
                    distanceSquared = candidateDistanceSquared;
                    result = candidateResult;
                    stepHeight = candidateStep;
                }
            }
            collisionKnown = reads.known() && !clamped;
        }
        return new Simulation(movement, result, friction, speed, acceleration, collisionKnown, zeroInput, onGround, stepHeight, slowdown != null);
    }

    // collisionKnown: every block read of the move was known, and the move was not clamped (longer than 16, MovementCollision.clampMovement).
    private sealed interface Outcome permits Simulation, Immobile {
        boolean collisionKnown();
    }

    private record Immobile() implements Outcome {
        public boolean collisionKnown() {
            return true;
        }
    }

    // stepHeight: the auto step used. slowed: a block slowdown scaled this move, so the velocity after the move is zero.
    private record Simulation(Vec3 movement, Collider.MoveResult collision, float friction, float speed, float acceleration, boolean collisionKnown,
                              boolean zeroInput, boolean onGround, float stepHeight, boolean slowed) implements Outcome {}

    private void commitOutcome(final EntityCache cache, final Outcome outcome) {
        if (outcome instanceof Immobile) {
            this.player.velocity = Vec3.ZERO.clone();
            this.vehicle.horseGroundFromTravel = true;
            endStandSliding();
            this.player.getMovementTrace().log("horse noai: immobile=true slidingPossible=" + cache.getHorseImmobility().slidingPossible()
                    + " mobJumpingPossible=" + cache.getHorseImmobility().mobJumpingPossible());
            return;
        }
        if (this.invalidateJumpOnTravel) {
            this.vehicle.invalidateHorseJump();
        }
        applySimulation(cache, (Simulation) outcome);
        if (this.matchedClaimEligible && outcome.collisionKnown()) {
            this.vehicle.markHorsePhysicsOutcome(this.player, cache);
        }
    }

    // Only when the horse is not standing (horseStanding is FALSE).
    // The actor's sliding flag is cleared later, once the input is accepted (VehicleData.finishSlidingClear).
    private void endStandSliding() {
        if (Boolean.FALSE.equals(this.vehicle.horseStanding)) {
            this.vehicle.horseStandSliding = false;
            this.vehicle.pendingSlidingClear = true;
        }
    }

    private void applySimulation(final EntityCache cache, final Simulation simulation) {
        this.vehicle.yaw = this.travelYaw;
        this.vehicle.pitch = this.travelPitch;
        this.vehicle.horseGroundFromTravel = true;
        final Vec3 movement = simulation.movement();
        final Collider.MoveResult result = simulation.collision();
        final float friction = simulation.friction();
        float vx = movement.x, vy = movement.y, vz = movement.z;
        final Vec3 moved = result.clippedMovement();
        final float startY = this.player.position.y;

        final Vec3 stopped = VehicleTicker.stopOnCollision(this.player, movement, moved, vx, vy, vz);
        vx = stopped.x;
        vy = stopped.y;
        vz = stopped.z;
        if (simulation.slowed()) {
            // applySlowdownOnMove: the move was scaled, the velocity is zero and the slowdown is used up.
            vx = vy = vz = 0;
            this.player.getMovementTrace().log("horse: block slowdown " + this.vehicle.slowdown + " applied, velocity zeroed");
            this.vehicle.slowdown = null;
        }
        final boolean wasOnGround = simulation.onGround();
        final float differenceY = Math.abs(moved.y - movement.y);
        this.vehicle.onGround = moved.y != movement.y && movement.y < 0 || wasOnGround && differenceY <= GenericMath.FLT_EPSILON && movement.y == 0;
        if (this.mountTrial == null && !wasOnGround && this.vehicle.onGround) {
            this.vehicle.horseStanding = false;
        }
        boolean landed = cache.getHorseGround().travel(result.requestedMovement().y, moved.y, simulation.collisionKnown());
        // In the mount window, only a kept FALSE ground history from the client can prove a landing.
        if (this.mountTrial != null && landed) {
            this.vehicle.horseStanding = false;
        }
        endStandSliding();
        // driver, TAMED and SADDLED hold for the whole tick (see the class comment).
        if (landed && this.vehicle.matchesMountedEntity(cache) && cache.hasFirstPlayerPassenger(this.player.uniqueEntityId)) {
            this.vehicle.horseLanded();
        }

        // Travel system order after the move (registerActorTravelSystems 0x1015ef09c): water drag, gravity, normal drag, friction, climb out of liquid.
        // In water: see WATER_DRAG. On land: gravity first, then vertical drag; horizontal friction with the epsilon (tickNormalFriction 0x105b713bc).
        if (this.inWater) {
            vx *= friction;
            vy *= WATER_DRAG;
            vz *= friction;
            vy -= WATER_GRAVITY;
        } else {
            vy = (vy - GRAVITY) * VERTICAL_DRAG;
            vx = Math.abs(vx) <= GenericMath.FLT_EPSILON ? 0 : vx * friction;
            vz = Math.abs(vz) <= GenericMath.FLT_EPSILON ? 0 : vz * friction;
        }
        this.vehicle.box = result.finalBox();
        if (this.inWater && this.player.horizontalCollision) {
            // ClimbOutOfLiquid (0x105b3cd8c): after drag and gravity, a horizontal collision in water sets vy = 0.3 (CLIMB_OUT_VELOCITY) if the box
            // moved by (vx, start y - end y + 0.6 + vy, vz) holds no liquid and hits no block. Same test as the player's edge hop. Lava is not modeled.
            final float lift = (startY - VehicleData.boxPosition(this.vehicle.box).y) + CLIMB_OUT_LIFT + vy;
            final Box climb = this.vehicle.box.offset(vx, lift, vz);
            if (this.player.compensatedWorld.noCollision(climb) && !this.player.containsFluid(climb)) {
                vy = CLIMB_OUT_VELOCITY;
                this.player.getMovementTrace().log("horse: climb out of liquid, y velocity set to " + CLIMB_OUT_VELOCITY);
            }
        }
        final Vec3 inside = this.insideBlocks(vx, vy, vz);
        vx = inside.x;
        vy = inside.y;
        vz = inside.z;

        this.player.velocity = new Vec3(vx, vy, vz);
        this.player.setPos(VehicleData.boxPosition(this.vehicle.box));

        this.player.getMovementTrace().log("horse: speed=" + simulation.speed() + " acceleration=" + simulation.acceleration() + " friction=" + friction
                + " inWater=" + this.inWater + " stepHeight=" + simulation.stepHeight() + " yaw=" + this.vehicle.yaw
                + " clientYaw=" + this.vehicle.vehicleRotation.getY() + " pitch=" + this.vehicle.pitch + " movement=" + movement + " moved=" + moved);
        this.player.getMovementTrace().log("horse stand: standing=" + this.vehicle.horseStanding + " sliding=" + this.vehicle.horseStandSliding
                + " zeroInput=" + simulation.zeroInput());
    }

    /**
     * The blocks the horse is inside after its move. Client: EntityInsideSystemImpl, callback 0x10514b458. The callback has no actor type filter,
     * so it runs for a horse too. A horse has no box grow (only boats, minecarts and projectiles set one), so the range is floor(min + 0.001) to
     * floor(max - 0.001) of the box after the move.
     * - Honey (tickInsideHoneyBlock 0x10514b7fc), not while the horse is in water: per honey block, x and z velocity times 0.4, y velocity at least -0.12.
     * - Cobweb (0.25, 0.05, 0.25), powder snow (0.9, 1.5, 0.9) and sweet berry bush (0.8, 0.75, 0.8, InsideSlowingSweetBerryBush 0x104a96ae0) set the
     *   slowdown for the next move (vehicle.slowdown). A second block keeps the smaller value per axis.
     *   In-game testing showed a horse in a cobweb moves the fresh ground acceleration times 0.25 each tick, with the velocity zeroed each move.
     * - Bubble column: we use the player rule (AbstractBoarBlockState.entityInside). The client's bubble column code was not read for a horse.
     * We run this after friction and gravity, the same order as the player's applyEffectsFromBlocks. Not checked: that the client uses this order for a horse.
     * Not done: the Weaving effect on the horse, lily pads (boats only), the sweet berry gate.
     */
    private Vec3 insideBlocks(float vx, float vy, float vz) {
        final Box box = this.vehicle.box;
        final int minX = GenericMath.floor(box.minX + 0.001F), minY = GenericMath.floor(box.minY + 0.001F), minZ = GenericMath.floor(box.minZ + 0.001F);
        final int maxX = GenericMath.floor(box.maxX - 0.001F), maxY = GenericMath.floor(box.maxY - 0.001F), maxZ = GenericMath.floor(box.maxZ - 0.001F);
        int honey = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    final BoarBlockState block = this.player.compensatedWorld.getBlockState(x, y, z, 0);
                    if (block.is(Blocks.HONEY_BLOCK)) {
                        honey++;
                    } else if (block.is(Blocks.COBWEB)) {
                        VehicleTicker.slowDownInCobweb(this.vehicle);
                    } else if (block.is(Blocks.POWDER_SNOW)) {
                        VehicleTicker.slowDownInPowderSnow(this.vehicle);
                    } else if (block.is(Blocks.SWEET_BERRY_BUSH)) {
                        VehicleTicker.slowDown(this.vehicle, 0.8F, 0.75F, 0.8F);
                    } else if (block.is(Blocks.BUBBLE_COLUMN)) {
                        final boolean drag = block.get(Properties.DRAG);
                        final boolean top = this.player.compensatedWorld.getBlockState(x, y + 1, z, 0).isAir();
                        final float before = vy;
                        if (top) {
                            vy = drag ? Math.max(-0.9F, vy - 0.03F) : Math.min(1.8F, vy + 0.1F);
                        } else {
                            vy = drag ? Math.max(-0.3F, vy - 0.03F) : Math.min(0.7F, vy + 0.06F);
                        }
                        this.player.getMovementTrace().log("horse: bubble column at " + x + "," + y + "," + z + " top=" + top
                                + " y velocity " + before + " -> " + vy);
                    }
                }
            }
        }
        if (!this.inWater) {
            final Vec3 honeyed = VehicleTicker.slowDownOnHoney(honey, vx, vy, vz);
            vx = honeyed.x;
            vy = honeyed.y;
            vz = honeyed.z;
        }
        if (honey > 0 || this.vehicle.slowdown != null) {
            this.player.getMovementTrace().log("horse: inside honey=" + honey + " (in water " + this.inWater + ") next slowdown=" + this.vehicle.slowdown);
        }
        return new Vec3(vx, vy, vz);
    }

    private boolean isOnGround(final EntityCache cache) {
        final Box box = cache.getDimensions().getBoxAt(this.player.position.x, this.player.position.y, this.player.position.z);
        final Vec3 probe = new Vec3(0, -GROUND_PROBE, 0);
        return Collider.collideBox(this.player, box, probe, 0.0F, false).clippedMovement().y != probe.y;
    }

    @Override
    public String name() {
        return "horse";
    }
}
