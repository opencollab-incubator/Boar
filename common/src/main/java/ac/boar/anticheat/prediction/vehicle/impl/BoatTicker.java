package ac.boar.anticheat.prediction.vehicle.impl;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.collision.Collider;
import ac.boar.anticheat.collision.DepenetrationState;
import ac.boar.anticheat.collision.OneWayCollision;
import ac.boar.anticheat.compensated.cache.entity.BoatBuoyancy;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.VehicleData;
import ac.boar.anticheat.prediction.PredictionState;
import ac.boar.anticheat.prediction.UncertainRunner;
import ac.boar.anticheat.prediction.ticker.base.EntityTicker;
import ac.boar.anticheat.prediction.vehicle.VehicleTicker;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.block.Blocks;
import ac.boar.mappings.block.Properties;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.common.NamedDefinition;

import java.util.ArrayList;
import java.util.List;

// Predicts the boat the player drives, the same way the client does.
// Order per tick (simulate): liquid push, friction, paddles, turn and thrust, move and collide, blocks inside, gravity, float.
// Client sources: BoatMoveFrictionSystem, BoatPaddleInputSystem, BoatMoveControlSystem, block slowdown, and the blocks the boat is inside
// (bubble columns, cobweb, powder snow, honey, lily pads).
// Not simulated: wave (bobbing) phase and liquid auto-step. Vanilla boats use movement_type none and no auto-step; in-game testing showed this.
// A leash only changes the air friction (see friction). Its pull runs on the server and skips a boat a player rides.
public class BoatTicker extends VehicleTicker {
    // Lily pads the last simulate() run broke (see insideBlocks). Applied to the world once, for the run we keep, at the end of tick().
    private List<Vector3i> lilyBreaks = new ArrayList<>();
    // This tick is the first one the rider drives, or an interpolation step reset the boat this tick (see the liquid push in simulate).
    private boolean firstControlTick, serverStepApplied;
    // Start position of the last takeover run that moved the boat (set by rerun). takeover() returns it when that run is kept.
    private Vec3 chosenStart;
    private static final float WATER_FRICTION = 0.9F;
    private static final float AIR_FRICTION = 1.0F;
    // Air friction of a leashed boat (Boat::reloadHardcodedClient 0x1093e9604: 0.91 when the level's base game version is 1.21.90 or later, or "*").
    // Not covered: levels with an older base game version. We always use 0.91.
    private static final float LEASHED_AIR_FRICTION = 0.91F;
    private static final float AIR_BLOCK_FRICTION = 0.9F;
    private static final float PADDLE_FORCE = 0.01375F;
    private static final float PADDLE_SPEED = 3.0F;
    private static final float PADDLE_SPEED_DECAY = 0.1F;
    private static final int PADDLE_PULSE_TICKS = 10;
    private static final float TORQUE_ARM = 3.0F;
    private static final float SLOW_SPEED_SQR = 0.010000001F;
    private static final float SLOW_TURN_BOOST = 1.6F;
    private static final float TURN_SCALE = 10.0F;
    private static final float GRAVITY = 0.04F;
    private static final float GRAVITY_DRAG = 0.98F;
    private static final float BUTTON_MIN_SPEED = 2.5F;
    private static final float BUTTON_SPEED_DECAY = 0.05F;
    private static final float BUTTON_STOP_SPEED = 0.01F;

    public BoatTicker(BoarPlayer player, VehicleData vehicle) {
        super(player, vehicle);
    }

    // Fall back only when we cannot simulate. Never fall back just because the client says so.
    @Override
    public void tick() {
        // We do not simulate boat pitch. We use the pitch the client sends.
        // After the rider became the driver (awaitingBoatFormatInput), only an input that claims this boat with a finite pitch sets it.
        if (!this.vehicle.awaitingBoatFormatInput || this.inputHasPitchForThisBoat()) {
            this.vehicle.pitch = this.vehicle.vehicleRotation.getX();
        }
        EntityCache cache = this.player.compensatedWorld.getEntity(this.vehicle.vehicleRuntimeId);
        if (!this.vehicle.matchesMountedEntity(cache)) {
            cache = null;
        }
        if (cache == null && this.vehicle.awaitingBoatFormatInput && this.vehicle.matchesMountedEntity(this.vehicle.lastKnownEntity)) {
            // The boat is missing from the entity cache for now. Keep simulating the last known boat.
            // Never use the rider's position to restart it.
            cache = this.vehicle.lastKnownEntity;
            this.vehicle.needsVehicleRestart = true;
        }
        if (cache == null) {
            this.holdWithoutVehicle("no boat entity");
            return;
        }

        // The rider is always the driver here: VehicleTicker.forVehicle builds a BoatTicker only for the driver,
        // and only acks change driver, never during a tick. So there is no driver test and no back-seat case in this class.
        final boolean firstControl = this.vehicle.ticks == 0;
        this.firstControlTick = firstControl;
        if (firstControl) {
            // Set the boat's start position first, before the unloaded-chunk check below. Then run normal physics on the same tick.
            this.mountActor(cache);
        }

        if (!this.player.compensatedWorld.isChunkLoadedAt(this.player.position.x, this.player.position.z)) {
            // Same as on foot in an unloaded chunk: no movement.
            this.player.getMovementTrace().log("boat: unloaded chunk, held still");
            this.player.velocity = Vec3.ZERO.clone();
            this.vehicle.yawVelocity = 0;
            return;
        }

        if (cache.isLocallySimulatedBoat()) {
            final EntityCache.ControlledBoatStep step = cache.takeControlledBoatStep();
            if (step != null) {
                // Interpolation (smooth move to a target) runs before physics. While it has steps left, it sets velocity to zero,
                // even when the target equals the boat position. Thrust and gravity below can make velocity nonzero again.
                this.player.setPos(step.position().clone());
                this.player.velocity = Vec3.ZERO.clone();
                this.vehicle.box = step.box();
                this.vehicle.pitch = step.pitch();
                this.vehicle.yaw = step.yaw();
                this.player.getMovementTrace().log("boat: applied server interpolation step position=" + this.player.position
                        + " velocity=" + this.player.velocity + " pitch=" + this.vehicle.pitch + " yaw=" + this.vehicle.yaw);
                this.serverStepApplied = true;
            }
        }
        final UncertainRunner uncertain = new UncertainRunner(this.player);
        final var takeoverStart = firstControl ? uncertain.boatTakeoverStart(cache) : null;
        final Vec3 canonicalStart = this.player.position.clone();
        final BoatTickState startState = takeoverStart == null ? null : BoatTickState.capture(this.player, cache);
        this.simulate(cache);
        this.publishActor(cache, takeoverStart == null ? canonicalStart : this.takeover(cache, uncertain, takeoverStart, startState, canonicalStart));
        this.breakLilyPads();
    }

    // True when this input claims client prediction for this boat and has a finite pitch.
    private boolean inputHasPitchForThisBoat() {
        final boolean claimsThisBoat = this.vehicle.inputClaimsClientPrediction && this.vehicle.predictedVehicleUniqueId == this.vehicle.vehicleUniqueId;
        return claimsThisBoat && this.vehicle.vehicleRotationPresent && Float.isFinite(this.vehicle.vehicleRotation.getX());
    }

    // InsideWaterlilyBlockSystemImpl::tickDestroyWaterlily (0x104a99a44) runs on the client for its own boat: each lily pad the boat was inside after
    // its move becomes air in the client's world right away, before the server's own block update arrives.
    private void breakLilyPads() {
        for (final Vector3i pos : this.lilyBreaks) {
            if (!this.player.compensatedWorld.getBlockState(pos.getX(), pos.getY(), pos.getZ(), 0).is(Blocks.LILY_PAD)) {
                continue;
            }
            this.player.compensatedWorld.updateBlock(pos, 0, this.player.mappingInfo.airId());
            this.player.getMovementTrace().log("boat: broke lily pad at " + pos);
        }
    }

    // First control after a takeover. The client can start from another point of the boat's recent path (UncertainRunner.boatTakeoverStart).
    // We rerun the tick with the start yaw turned to the client's (boatTakeoverYawShift), then with the start shifted by what is left (boatTakeoverShift).
    // A result is kept only if UncertainRunner.acceptBoatTakeover passes and every collision read was known. Returns the start position we keep.
    private Vec3 takeover(final EntityCache cache, final UncertainRunner uncertain, final UncertainRunner.BoatTakeoverStart takeoverStart,
                          final BoatTickState startState, final Vec3 canonicalStart) {
        final float yawShift = uncertain.boatTakeoverYawShift(takeoverStart, this.vehicle.yaw);
        final BoatTickState canonical = BoatTickState.capture(this.player, cache);
        final List<Vector3i> canonicalLilyBreaks = this.lilyBreaks;
        boolean changed = false, keep = false;
        this.chosenStart = canonicalStart;
        try {
            boolean known = true;
            if (yawShift != 0) {
                changed = true;
                known = this.rerun(cache, startState, yawShift, null);
            }
            final Vec3 shift = known ? uncertain.boatTakeoverShift(takeoverStart, null) : null;
            if (shift != null) {
                changed = true;
                final boolean shiftKnown = this.rerun(cache, startState, yawShift, shift);
                keep = shiftKnown && uncertain.acceptBoatTakeover(yawShift);
                // Missed: shift once more by what is left. A floor push-out can move two starts differently.
                final Vec3 second = !keep && shiftKnown ? uncertain.boatTakeoverShift(takeoverStart, shift) : null;
                if (second != null) {
                    keep = this.rerun(cache, startState, yawShift, second) && uncertain.acceptBoatTakeover(yawShift);
                }
            } else if (yawShift != 0 && known) {
                // The turned run may already end at the claimed position.
                keep = uncertain.acceptBoatTakeover(yawShift);
            }
            // Last try: start from the center of the envelope's set (UncertainRunner.boatTakeoverCenterShift). Our own start can be off the set.
            final Vec3 center = !keep && known ? uncertain.boatTakeoverCenterShift(takeoverStart) : null;
            if (center != null) {
                changed = true;
                keep = this.rerun(cache, startState, yawShift, center) && uncertain.acceptBoatTakeover(yawShift);
            }
        } finally {
            if (changed && !keep) {
                canonical.restore(this.player, cache);
                this.lilyBreaks = canonicalLilyBreaks;
            }
        }
        return keep ? this.chosenStart : canonicalStart;
    }

    // One takeover run: the tick again from startState, with the start yaw turned by yawShift. When offset is not null, the boat is also moved by offset
    // and chosenStart is set to that start. Returns true when every collision read in the run was known.
    private boolean rerun(final EntityCache cache, final BoatTickState startState, final float yawShift, final Vec3 offset) {
        startState.restore(this.player, cache);
        this.vehicle.yaw = startState.yaw() + yawShift;
        if (offset != null) {
            this.vehicle.box = this.vehicle.box.offset(offset.x, offset.y, offset.z);
            this.player.setPos(VehicleData.boxPosition(this.vehicle.box), false);
            this.chosenStart = this.player.position.clone();
        }
        try (final var reads = this.player.compensatedWorld.trackBlockReads()) {
            this.simulate(cache);
            return reads.known();
        }
    }

    private record PaddleState(int rowStart, int lastRowStart, float speed) {
        static PaddleState capture(final VehicleData.Paddle paddle) {
            return new PaddleState(paddle.rowStart, paddle.lastRowStart, paddle.speed);
        }

        void restore(final VehicleData.Paddle paddle) {
            paddle.rowStart = this.rowStart;
            paddle.lastRowStart = this.lastRowStart;
            paddle.speed = this.speed;
        }
    }

    // Box is immutable (all fields final), so the box is stored and restored without a copy.
    private record BoatTickState(PredictionState playerState, Box box, float pitch, float yaw, float yawVelocity, boolean onGround, int paddleTick,
                                 PaddleState first, PaddleState second, DepenetrationState depenetration, Vec3 slowdown) {
        static BoatTickState capture(final BoarPlayer player, final EntityCache cache) {
            final var vehicle = player.vehicleData;
            return new BoatTickState(PredictionState.capture(player), vehicle.box, vehicle.pitch, vehicle.yaw, vehicle.yawVelocity, vehicle.onGround,
                    vehicle.paddleTick, PaddleState.capture(vehicle.firstPaddle), PaddleState.capture(vehicle.secondPaddle),
                    cache.getDepenetrationState().copy(), vehicle.slowdown == null ? null : vehicle.slowdown.clone());
        }

        void restore(final BoarPlayer player, final EntityCache cache) {
            this.playerState.restore(player);
            final var vehicle = player.vehicleData;
            vehicle.setActorBox(this.box, cache.getDimensions());
            vehicle.pitch = this.pitch;
            vehicle.yaw = this.yaw;
            vehicle.yawVelocity = this.yawVelocity;
            vehicle.onGround = this.onGround;
            vehicle.paddleTick = this.paddleTick;
            this.first.restore(vehicle.firstPaddle);
            this.second.restore(vehicle.secondPaddle);
            cache.getDepenetrationState().restore(this.depenetration);
            vehicle.slowdown = this.slowdown == null ? null : this.slowdown.clone();
        }
    }

    private void mountActor(final EntityCache cache) {
        EntityCache.BoatControlStartPose seed = cache.takeBoatControlStartPose();
        if (seed == null) {
            // The last known boat is not in the entity map, so its normal entity tick did not run.
            // Take the start pose from follower history the same way that tick would. Then finish the reset to local control before moving.
            final var history = cache.getFollowerHistory();
            cache.captureBoatControlStartPose(history);
            cache.resetToLocalControl(history);
            seed = cache.takeBoatControlStartPose();
        }
        this.player.setPos(seed.position().clone());
        this.player.velocity = seed.velocity().clone();
        this.vehicle.setActorBox(seed.box(), cache.getDimensions());
        this.vehicle.pitch = seed.pitch();
        this.vehicle.yaw = seed.yaw();
        this.vehicle.yawVelocity = seed.angularVelocity();
        this.vehicle.onGround = seed.onGround();
        this.vehicle.slowdown = null;
        final var before = seed.before();
        this.vehicle.firstControlRenderPose = new VehicleData.BoatRenderPose(new Vec3(before.x(), before.y() - cache.getYOffset(), before.z()), before.yaw());
        this.player.horizontalCollision = false;
        this.player.verticalCollision = false;
        final var startHistory = cache.getFollowerHistory();
        final String envelopeControl = startHistory == null || startHistory.envelopeControlSet() == null
                ? "none"
                : "trusted=" + startHistory.envelopeControlTrusted() + " " + startHistory.envelopeControlSet();
        final String startLine = "boat: first control start pose position=" + this.player.position + " velocity=" + this.player.velocity
                + " pitch=" + this.vehicle.pitch + " yaw=" + this.vehicle.yaw + " angularVelocity=" + this.vehicle.yawVelocity + " box=" + this.vehicle.box
                + " dpFlags=" + cache.getDepenetrationState().flags() + " before=(" + before.x() + "," + before.y() + "," + before.z() + ")"
                + " envControl{" + envelopeControl + "}";
        this.player.getMovementTrace().log(startLine);
        if (Boar.getConfig().debugMode()) {
            Boar.debug(this.player.getSession().name() + ": [movement-debug] " + startLine, Boar.DebugMessage.INFO);
        }
    }

    private void simulate(final EntityCache cache) {
        this.lilyBreaks = new ArrayList<>();
        // Liquid push: LiquidPhysicsSystem::_liquidBlockFetch pushes the boat along flowing water before the friction pick and both friction multiplies
        // (registerActorNormalTickSystems runs it before the boat move systems). Same code and speed as a player (0.014 water, 0.0035 lava),
        // from the box the last move left. In-game testing matched: from rest in flowing water, speed = (v + 0.014) * 0.9 * 0.9 each tick.
        // Not on the first tick the rider drives: the client push (LiquidPhysicsSystem::_markForLiquidFlow) only sees entities with
        // ControlledByLocalInstanceComponent. In-game testing showed the first push one tick later. Which system adds that component is not checked.
        // Not on a tick where an interpolation step (a server move of the driven boat, see tick()) reset the boat: the client pushes first,
        // then the step sets velocity to zero. In-game testing showed this order; it was not read in client code.
        final Vec3 push = this.firstControlTick || this.serverStepApplied
                ? new Vec3(0, 0, 0)
                : new EntityTicker(this.player).fluidPushFor(this.vehicle.boxAt(this.player.position, cache.getDimensions()));
        if (push.lengthSquared() > 0) {
            this.player.velocity = this.player.velocity.add(push);
            this.player.getMovementTrace().log("boat: liquid push " + push);
        }
        final BoatBuoyancy buoyancy = cache.getBoatBuoyancy();
        final Friction frictionState = this.friction(buoyancy, cache);
        final float friction = frictionState.value();
        float vx = this.player.velocity.x * friction;
        float vz = this.player.velocity.z * friction;
        this.vehicle.yawVelocity *= friction;

        // BoatPaddleInputSystem: touch with the classic layout uses the paddle buttons. All other input uses the MoveVector.
        final boolean buttons = this.player.inputMode == InputMode.TOUCH && this.vehicle.interactionModel == InputInteractionModel.CLASSIC;
        float firstIntent = 0, secondIntent = 0;
        if (buttons) {
            // Buttons are not mirrored: PADDLE_LEFT drives the first paddle.
            this.updateButtonPaddleSpeed(this.vehicle.firstPaddle, this.vehicle.paddleLeft);
            this.updateButtonPaddleSpeed(this.vehicle.secondPaddle, this.vehicle.paddleRight);
        } else {
            // The names are mirrored: pressing D (x = -1) drives the first paddle and sends PADDLE_RIGHT.
            float x = this.vehicle.motion.getX();
            float y = this.vehicle.up ? 1.0F : Math.max(-1.0F, Math.min(this.vehicle.motion.getY(), 1.0F));
            float magnitude = (float) Math.sqrt(x * x + y * y);
            if (y < 0) {
                magnitude *= -0.15F;
                x = -x;
            }
            firstIntent = Math.max(0.0F, 1.0F - Math.max(x, 0.0F)) * magnitude;
            secondIntent = (1.0F + Math.max(-1.0F, Math.min(x, 0.0F))) * magnitude;

            this.updatePaddleSpeed(this.vehicle.firstPaddle, firstIntent);
            this.updatePaddleSpeed(this.vehicle.secondPaddle, secondIntent);
        }
        this.vehicle.paddleTick++;

        // BoatMoveControlSystem::boatControl
        // BoatMoveFrictionSystem turns off thrust in dry air. The paddle animation and timing still advance.
        final float firstPaddleForce = this.paddleForce(this.vehicle.firstPaddle);
        final float secondPaddleForce = this.paddleForce(this.vehicle.secondPaddle);
        final float firstForce = frictionState.canPaddle() ? firstPaddleForce : 0;
        final float secondForce = frictionState.canPaddle() ? secondPaddleForce : 0;

        float thrust = firstForce + secondForce;
        float torque = firstForce * TORQUE_ARM + secondForce * -TORQUE_ARM;
        if (vx * vx + vz * vz < SLOW_SPEED_SQR && torque != 0) {
            thrust *= friction;
            torque *= SLOW_TURN_BOOST;
        }

        this.vehicle.yawVelocity = (torque * TURN_SCALE + this.vehicle.yawVelocity) * friction;
        this.vehicle.yaw += this.vehicle.yawVelocity;

        final float angle = (90.0F - this.vehicle.yaw) * MathUtil.DEGREE_TO_RAD;
        vx = (vx + thrust * (float) Math.sin(angle)) * friction;
        vz = (vz + thrust * (float) Math.cos(angle)) * friction;

        // Move and collide. For y, use the velocity from the end of the last tick plus the liquid push. Friction and thrust only change x and z.
        float vy = this.player.velocity.y;
        final EntityDimensions dimensions = cache.getDimensions();
        final Box box = this.vehicle.boxAt(this.player.position, dimensions);
        Vec3 movement = new Vec3(vx, vy, vz);
        // BlockMovementSlowdownMultiplierSystem::applySlowdownOnMove (0x1050a710c, the first move system): a slowdown set after the last move
        // scales this move per axis, sets the velocity to zero and is used up.
        final Vec3 slowdown = this.vehicle.slowdown;
        if (slowdown != null) {
            movement = movement.multiply(slowdown);
            vx = vy = vz = 0;
            this.vehicle.slowdown = null;
            this.player.getMovementTrace().log("boat: block slowdown " + slowdown + " applied, velocity zeroed");
        }
        final boolean pushTowardsClosestSpace = cache.hasFlag(EntityFlag.PUSH_TOWARDS_CLOSEST_SPACE);
        cache.getDepenetrationState().removeSeparatedOneWayBoxes(box);
        final Collider.MoveResult result = Collider.collideActorBox(this.player, box, movement, cache.getDepenetrationState(), pushTowardsClosestSpace);
        final Vec3 moved = result.clippedMovement();

        final Vec3 stopped = VehicleTicker.stopOnCollision(this.player, movement, moved, vx, vy, vz);
        vx = stopped.x;
        vy = stopped.y;
        vz = stopped.z;
        this.vehicle.onGround = moved.y != movement.y && movement.y < 0;

        this.vehicle.box = result.finalBox();
        this.player.setPos(VehicleData.boxPosition(this.vehicle.box));
        OneWayCollision.collectInsideActor(this.player, cache.getDepenetrationState(), this.vehicle.box);

        // Blocks the boat is inside act before gravity (see insideBlocks). Gravity comes before the float step.
        // Buoyancy (floating) checks use the new native origin, after the move.
        final Vec3 inside = this.insideBlocks(vx, vy, vz, frictionState.water());
        vx = inside.x;
        vy = inside.y;
        vz = inside.z;
        final BuoyancyProbe floating = this.buoyancyProbe(buoyancy);
        if (buoyancy.appliesGravity()) {
            vy = (vy - GRAVITY) * GRAVITY_DRAG;
        }
        if (floating.resurface() || (floating.canFloat() && buoyancy.movement() == BoatBuoyancy.Movement.NONE)) {
            vy = this.floatVelocity(buoyancy, floating, vy);
        }
        this.player.velocity = new Vec3(vx, vy, vz);

        // Developer debug only: a long line on every boat tick.
        if (Boar.getInstance().getPlatform().developerDebug()) {
            this.player.getMovementTrace().log("boat: buttons=" + buttons + " intent=(" + firstIntent + "," + secondIntent + ")"
                    + " paddleSpeed=(" + this.vehicle.firstPaddle.speed + "," + this.vehicle.secondPaddle.speed + ") thrust=" + thrust + " torque=" + torque
                    + " friction=" + friction + " canPaddle=" + frictionState.canPaddle() + " yawVelocity=" + this.vehicle.yawVelocity
                    + " predictedYaw=" + this.vehicle.yaw + " clientYaw=" + this.vehicle.vehicleRotation.getY() + " box=" + dimensions
                    + " movement=" + movement + " moved=" + moved + " buoyancy=" + buoyancy.enabled() + " gravity=" + buoyancy.appliesGravity()
                    + " float=" + floating + " floatMode=" + buoyancy.movement() + " autoStep=" + buoyancy.autoStep());
        }
    }

    /**
     * Applies the blocks the boat is inside after its move (EntityInsideSystemImpl::EntityInside::singleTick 0x1051884b8, callback 0x1051818ec).
     * Range: SpatialQueryUtility::forEachBlockInAABB (0x105bd6bc8) grows the box by g on every side, then 0.001 in:
     * blocks floor(min - g + 0.001) to floor(max + g - 0.001). For a boat g = 0.5 (Boat::Boat sets InsideGenericBlockComponent+0x34 to 0.5).
     * Every block in range counts; there is no shape test. So the honey floor under the boat counts too.
     * - Bubble column (tickBoatInsideBubbleColumnBlock): each column block with a non-air block above pushes once.
     *   Up: y velocity + 0.06 (at most 0.7). Down (drag): y velocity - 0.03 (at least -0.3). The top block (air above) only makes particles and sound.
     *   The column cells hold water in the extra layer (the server sends it). Without that water there is no float step.
     * - Cobweb (0.25, 0.05, 0.25) and powder snow (0.9, 1.5, 0.9) set the slowdown for the next move (vehicle.slowdown, used in simulate).
     *   A second block takes the smaller value per axis.
     * - Honey (tickInsideHoneyBlock 0x10514b7fc), not when the boat is in water: per honey block, x and z velocity times 0.4, y velocity at least -0.12.
     * - Lily pad (boats only): velocity times 0.5 per lily pad, and the client breaks it (see breakLilyPads).
     * In-game testing matched the bubble column, honey and lily pad rules.
     * Order: after the move and its collision stop, before gravity and the float step.
     * Not known: the order between these block kinds in one tick (two kinds at once was not seen in in-game testing).
     * Not done: sweet berry bush (a separate client system; whether it acts on a boat is not checked).
     */
    private Vec3 insideBlocks(float vx, float vy, float vz, final boolean inWater) {
        final Box box = this.vehicle.box;
        final float grow = 0.5F;
        final int minX = GenericMath.floor(box.minX - grow + 0.001F);
        final int minY = GenericMath.floor(box.minY - grow + 0.001F);
        final int minZ = GenericMath.floor(box.minZ - grow + 0.001F);
        final int maxX = GenericMath.floor(box.maxX + grow - 0.001F);
        final int maxY = GenericMath.floor(box.maxY + grow - 0.001F);
        final int maxZ = GenericMath.floor(box.maxZ + grow - 0.001F);
        int honey = 0, lily = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    final BoarBlockState block = this.player.compensatedWorld.getBlockState(x, y, z, 0);
                    if (block.is(Blocks.BUBBLE_COLUMN)) {
                        if (this.player.compensatedWorld.getBlockState(x, y + 1, z, 0).isAir()) {
                            continue;
                        }
                        final float before = vy;
                        vy = block.get(Properties.DRAG) ? Math.max(-0.3F, vy - 0.03F) : Math.min(0.7F, vy + 0.06F);
                        this.player.getMovementTrace().log("boat: bubble column at " + x + "," + y + "," + z + " y velocity " + before + " -> " + vy);
                    } else if (block.is(Blocks.COBWEB)) {
                        VehicleTicker.slowDownInCobweb(this.vehicle);
                    } else if (block.is(Blocks.POWDER_SNOW)) {
                        VehicleTicker.slowDownInPowderSnow(this.vehicle);
                    } else if (block.is(Blocks.HONEY_BLOCK)) {
                        honey++;
                    } else if (block.is(Blocks.LILY_PAD)) {
                        lily++;
                        this.lilyBreaks.add(Vector3i.from(x, y, z));
                    }
                }
            }
        }
        if (!inWater) {
            final Vec3 honeyed = VehicleTicker.slowDownOnHoney(honey, vx, vy, vz);
            vx = honeyed.x;
            vy = honeyed.y;
            vz = honeyed.z;
        }
        for (int i = 0; i < lily; i++) {
            vx *= 0.5F;
            vy *= 0.5F;
            vz *= 0.5F;
        }
        if (honey > 0 || lily > 0 || this.vehicle.slowdown != null) {
            this.player.getMovementTrace().log("boat: inside honey=" + honey + " (in water " + inWater + ") lily=" + lily
                    + " next slowdown=" + this.vehicle.slowdown);
        }
        return new Vec3(vx, vy, vz);
    }

    // Save only the result we picked. A takeover candidate that failed must never overwrite the boat pose in the cache.
    private void publishActor(final EntityCache cache, final Vec3 previousPosition) {
        if (this.vehicle.isLiveMount(this.player, cache) && cache.getCurrent() != null) {
            cache.getCurrent().setSimulatedBoatState(previousPosition, this.player.position, this.vehicle.box);
            cache.setActorPitch(this.vehicle.pitch);
            cache.setActorYaw(this.vehicle.yaw);
            cache.setActorOnGround(this.vehicle.onGround);
        }
    }

    // BoatMoveFrictionSystem::boatMoveFriction (0x105af49d8), with the state from the start of the tick.
    // The air value is 1.0, or 0.91 while the boat has the LEASHED flag. Water and ground values do not change with a leash.
    // The leash pull itself (TickLeashedEntitySystem::tickEntity 0x10098733c) is a server-only system and adds nothing while a player rides the boat.
    // water: the boat counts as in water this tick (the honey rule skips it then, client view Exclude<WasInWaterFlag>).
    private record Friction(float value, boolean canPaddle, boolean water) {}

    private Friction friction(final BoatBuoyancy buoyancy, final EntityCache cache) {
        final float air = cache.hasFlag(EntityFlag.LEASHED) ? LEASHED_AIR_FRICTION : AIR_FRICTION;
        final BuoyancyProbe floating = this.buoyancyProbe(buoyancy);
        if (floating.canFloat() || floating.resurface()) {
            return new Friction(WATER_FRICTION, true, true);
        }
        // The block at the rounded-down (floored) wire position. The wire position includes the boat offset.
        final int x = GenericMath.floor(this.player.position.x);
        final int y = GenericMath.floor(this.player.nativeOriginY);
        final int z = GenericMath.floor(this.player.position.z);
        // Material checks use the liquid block. Friction values come from the normal block.
        final BoarBlockState current = this.liquidBlock(x, y, z);
        if (current.is(Blocks.WATER)) {
            return new Friction(WATER_FRICTION, true, true);
        }

        final BoarBlockState below = this.player.compensatedWorld.getBlockState(x, y - 1, z, 0);
        if (current.isAir() || current.is(Blocks.SNOW)) {
            if (this.vehicle.onGround) {
                return new Friction(blockFriction(below), true, false);
            }
            return this.liquidBlock(x, y - 1, z).is(Blocks.WATER) ? new Friction(WATER_FRICTION, true, true) : new Friction(air, false, false);
        }

        if (!this.vehicle.onGround) {
            return new Friction(air, false, false);
        }

        // getFrictionBasedOnCollision: the current block counts only if its collision box has size on all three axes.
        final BoarBlockState block = this.player.compensatedWorld.getBlockState(x, y, z, 0);
        return new Friction(blockFriction(hasVolume(block) ? block : below), true, false);
    }

    private static float blockFriction(final BoarBlockState block) {
        // AirBlock has its own friction. It is used when the boat's center is over air but the hull still touches the ground.
        return block.isAir() ? AIR_BLOCK_FRICTION : block.getFriction();
    }

    private record BuoyancyProbe(boolean canFloat, boolean resurface) {
        private static final BuoyancyProbe NONE = new BuoyancyProbe(false, false);
    }

    private BuoyancyProbe buoyancyProbe(final BoatBuoyancy buoyancy) {
        if (!buoyancy.hasLiquids()) {
            return BuoyancyProbe.NONE;
        }

        final int x = GenericMath.floor(this.player.position.x);
        final int y = GenericMath.floor(this.player.nativeOriginY);
        final int z = GenericMath.floor(this.player.position.z);
        final BoarBlockState current = this.liquidBlock(x, y, z);
        final BoarBlockState above = this.liquidBlock(x, y + 1, z);
        final BlockDefinition currentDefinition = current.definition(this.player);
        final BlockDefinition aboveDefinition = above.definition(this.player);
        final int depth = liquidDepth(current, currentDefinition);
        final int level = depth < 8 ? depth + 1 : 1;
        final float cutoff = (float) (y + 1) - ((float) level / 9.0F + -0.11111111F);
        final boolean matchesCurrent = matchesLiquid(buoyancy, currentDefinition);
        final boolean matchesAbove = matchesLiquid(buoyancy, aboveDefinition);
        return new BuoyancyProbe(this.player.nativeOriginY < cutoff && matchesCurrent && !matchesAbove, matchesCurrent && matchesAbove);
    }

    private static boolean matchesLiquid(final BoatBuoyancy buoyancy, final BlockDefinition block) {
        // The buoyancy profile uses Bedrock block names. flowing_water and water are different names here.
        return block instanceof NamedDefinition definition && buoyancy.matches(definition.getIdentifier());
    }

    private static int liquidDepth(final BoarBlockState block, final BlockDefinition definition) {
        if (definition instanceof SimpleBlockDefinition simple) {
            final Object depth = simple.getState().getCompound("states").get("liquid_depth");
            return depth instanceof Number number ? number.intValue() : 0;
        }
        if (!block.is(Blocks.WATER) && !block.is(Blocks.LAVA)) {
            return 0;
        }
        try {
            final Integer depth = block.get(Properties.LEVEL);
            return depth == null ? 0 : depth;
        } catch (IllegalArgumentException absent) {
            // A configured block without LiquidDepth gets depth 0, which means level 1.
            return 0;
        }
    }

    private float floatVelocity(final BoatBuoyancy buoyancy, final BuoyancyProbe floating, final float velocity) {
        float submerged = 1.0F;
        if (floating.canFloat()) {
            final float fraction = this.player.nativeOriginY - (float) Math.floor(this.player.nativeOriginY);
            submerged = (buoyancy.base() - fraction) * 0.9F + 0.1F;
            // The client's clamp treats NaN (not a number) as zero.
            submerged = submerged > 0 ? submerged : 0;
            submerged = submerged > 1 ? 1 : submerged;
            if (submerged == 0) {
                return velocity;
            }
        }

        // movement_type none has no wave offset. Resurfacing ignores waves too.
        final float target = (submerged + -0.1F) * 0.15F;
        final float limit = velocity * 0.7F + 0.05F;
        return target < limit ? target : limit;
    }

    // BlockSource::getLiquidBlock: the extra block layer if it is not air, else the normal block.
    private BoarBlockState liquidBlock(final int x, final int y, final int z) {
        final BoarBlockState extra = this.player.compensatedWorld.getBlockState(x, y, z, 1);
        return extra.isAir() ? this.player.compensatedWorld.getBlockState(x, y, z, 0) : extra;
    }

    private static boolean hasVolume(final BoarBlockState state) {
        final List<Box> boxes = state.getCollisionBoxes();
        if (boxes.isEmpty()) {
            return false;
        }

        Box union = boxes.get(0);
        for (final Box box : boxes) {
            union = union.union(box);
        }
        return union.maxX - union.minX > 0 && union.maxY - union.minY > 0 && union.maxZ - union.minZ > 0;
    }

    private void updatePaddleSpeed(final VehicleData.Paddle paddle, final float intent) {
        final int tick = this.vehicle.paddleTick;
        if (intent == 0) {
            paddle.speed = 0;
        } else if (tick - paddle.rowStart >= PADDLE_PULSE_TICKS) {
            paddle.speed = intent * PADDLE_SPEED;
            paddle.rowStart = tick;
        } else {
            paddle.speed = Math.signum(intent) * Math.max(0.0F, Math.abs(intent * PADDLE_SPEED) - PADDLE_SPEED_DECAY);
        }
    }

    private void updateButtonPaddleSpeed(final VehicleData.Paddle paddle, final boolean held) {
        final int tick = this.vehicle.paddleTick;
        if (held) {
            if (paddle.rowStart < 0) {
                paddle.speed = tick - paddle.lastRowStart >= PADDLE_PULSE_TICKS ? PADDLE_SPEED : Math.max(BUTTON_MIN_SPEED, paddle.speed - BUTTON_SPEED_DECAY);
                paddle.rowStart = tick;
            } else {
                paddle.speed = tick - paddle.rowStart < PADDLE_PULSE_TICKS ? PADDLE_SPEED : Math.max(BUTTON_MIN_SPEED, paddle.speed - BUTTON_SPEED_DECAY);
            }
        } else {
            if (paddle.rowStart >= 0) {
                paddle.lastRowStart = paddle.rowStart;
                paddle.rowStart = -1;
            }
            paddle.speed = paddle.speed <= BUTTON_STOP_SPEED ? 0 : paddle.speed * 0.5F;
        }
    }

    private float paddleForce(final VehicleData.Paddle paddle) {
        final float force = paddle.speed * PADDLE_FORCE;
        if (force == 0) {
            paddle.rowStart = -1;
            return 0;
        }
        return force;
    }

    @Override
    public String name() {
        return "boat";
    }
}
