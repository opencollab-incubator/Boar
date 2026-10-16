package ac.boar.anticheat.player;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.ack.BoarAcknowledgmentTransport;
import ac.boar.anticheat.ack.BoarBatchedAcknowledgmentTransport;
import ac.boar.anticheat.check.api.holder.CheckHolder;
import ac.boar.anticheat.collision.util.CuboidBlockIterator;
import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.compensated.world.BlockPlacementTracker;
import ac.boar.anticheat.compensated.world.CompensatedWorldImpl;
import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.data.ItemUseTracker;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.data.enchantment.Enchantment;
import ac.boar.mappings.block.Blocks;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.player.accessor.EntityAccessor;
import ac.boar.anticheat.player.accessor.InventoryAccessor;
import ac.boar.anticheat.player.accessor.WorldAccessor;
import ac.boar.anticheat.player.data.BlockMappingInfo;
import ac.boar.anticheat.player.data.HorseJumpInput;
import ac.boar.anticheat.player.data.PlayerData;
import ac.boar.anticheat.player.data.VehicleType;
import ac.boar.anticheat.prediction.vehicle.FollowerHistory;
import ac.boar.anticheat.teleport.TeleportUtil;
import ac.boar.anticheat.util.LatencyUtil;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.MovementTrace;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.anticheat.validator.blockbreak.ServerBreakBlockValidator;
import ac.boar.anticheat.validator.inventory.ItemTransactionValidator;
import ac.boar.api.anticheat.model.MessageRecipient;
import ac.boar.api.anticheat.model.NetworkSession;
import ac.boar.mappings.block.BlockMappings;
import ac.boar.mappings.entity.Entity;
import ac.boar.mappings.entity.EntityDefinition;
import ac.boar.mappings.entity.EntityDefinitions;
import ac.boar.mappings.entity.EntityTypes;
import ac.boar.protocol.PacketInjector;
import io.netty.util.ReferenceCountUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.TrigMath;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import ac.boar.protocol.BoarConnection;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

public final class BoarPlayer extends PlayerData {
    @Getter
    private final NetworkSession session;
    @Getter
    private final BoarConnection connection;
    @Getter
    private final Entity entity;
    @Getter
    private final WorldAccessor worldAccessor;
    @Getter
    private final EntityAccessor entityAccessor;
    @Getter
    private final InventoryAccessor inventoryAccessor;

    public long runtimeEntityId, uniqueEntityId;
    public long acceptedInputCount, acceptedInputEpoch;
    // Kept here, not in VehicleData. VehicleData is replaced on each ride, so swapping between boats cannot skip the takeover grant cooldown.
    public long lastAcceptedBoatTakeoverInput = Long.MIN_VALUE;
    public final HorseJumpInput horseJumpInput = new HorseJumpInput();
    // Players from the player list, by uuid, with their unique entity id. Used before their entity is spawned.
    public final Map<UUID, Long> listedPlayers = new HashMap<>();

    // Players count differently than mobs in the passenger order of a vehicle.
    public boolean isPlayerEntity(final long uniqueId) {
        if (uniqueId == this.uniqueEntityId || this.listedPlayers.containsValue(uniqueId)) {
            return true;
        }

        final EntityCache cache = this.compensatedWorld.getTrackedEntityByUniqueId(uniqueId);
        return cache != null && cache.getType().is(EntityTypes.PLAYER);
    }

    // When the current input arrived (in nanoseconds). We use it as the client tick time.
    public long inputArrivalNs;
    public final FollowerHistory.ArrivalClock followerArrivalClock = new FollowerHistory.ArrivalClock();
    private static final float FOLLOWER_TICK_DISTANCE_SQUARED = 8.0F * 8.0F;
    // From our SEAT_OFFSET metadata: where we sit relative to the vehicle we ride.
    public Vector3f seatOffset = Vector3f.ZERO;

    @Getter
    private final TeleportUtil teleportUtil = new TeleportUtil(this);

    @Getter
    private final CheckHolder checkHolder = new CheckHolder(this);

    @Getter
    private final LatencyUtil latencyUtil = new LatencyUtil(this);

    // Records each tick of the movement prediction. Flushed to the log on a prediction failure.
    @Getter
    private final MovementTrace movementTrace = new MovementTrace(this);

    @Getter
    @Setter
    private BoarAcknowledgmentTransport ackTransport;

    // Platform-provided replay hook. The default platform installs BoarHandlerAdaptor here
    // (see BoarPlayerManager#installHandlers); platforms that bridge their own packet-event
    // stream must install their own injector, or injectClientPacket drops every packet.
    @Setter
    private PacketInjector packetInjector;

    // Lag compensation
    public final CompensatedWorldImpl compensatedWorld = new CompensatedWorldImpl(this);
    public final BlockPlacementTracker blockPlacements = new BlockPlacementTracker();
    public final CompensatedInventory compensatedInventory = new CompensatedInventory(this);

    // Validation
    public ServerBreakBlockValidator serverBreakBlockValidator;
    public final ItemTransactionValidator transactionValidator = new ItemTransactionValidator(this);

    @Getter
    private final ItemUseTracker itemUseTracker = new ItemUseTracker(this);

    @Getter
    private final Map<UUID, MessageRecipient> trackedDebugPlayers = new ConcurrentHashMap<>();

    // Whether mitigations are suppressed for this player: checks still run and violations are still
    // reported, but nothing is cancelled, corrected, rewound or kicked. Seeded per-player at
    // construction (see BoarPlayerManager#shouldDisableMitigations) and mutable afterwards, so an
    // integration can move a single player between arms at runtime without touching the config.
    @Setter
    private volatile boolean disableMitigations;

    public ScheduledFuture<?> future;

    private boolean timedOut;

    @SneakyThrows
    public BoarPlayer(NetworkSession session, BoarConnection connection, Entity entity,
                      BlockMappingInfo mappingInfo, WorldAccessor worldAccessor, EntityAccessor entityAccessor,
                      InventoryAccessor inventoryAccessor, Map<String, AttributeInstance> defaultAttributes,
                      boolean disableMitigations) {
        super(mappingInfo);

        this.session = session;
        this.connection = connection;
        this.entity = entity;
        this.disableMitigations = disableMitigations;

        this.worldAccessor = worldAccessor;
        this.entityAccessor = entityAccessor;
        this.inventoryAccessor = inventoryAccessor;

        this.attributes.putAll(defaultAttributes);
    }

    void serverTick() {
        if (!this.getLatencyUtil().hasInFlight()) {
            // If acks are pending, the next outbound batch flush emits an NSL covering them — skip the keepalive to avoid an extra wire ping.
            // The BedrockPeer ticks every 50ms so the flush will happen well before any timeout threshold.
            if (this.ackTransport instanceof BoarBatchedAcknowledgmentTransport batched && batched.hasPending()) {
                return;
            }
            sendLatencyStack();
            return;
        }

        if (!this.timedOut && System.currentTimeMillis() - this.getLatencyUtil().prevAcceptedTime > Boar.getConfig().maxLatencyWait()) {
            this.timedOut = true;
            // A timeout is not a cheat. Only remove the player from this server, so a proxied
            // network can send them to a fallback server instead of closing the connection.
            this.session.disconnectFromServer(Boar.getInstance().getAlertManager().getPrefix() + " Timed out!");
        }
    }

    public boolean isClosed() {
        return this.session.isClosed();
    }

    /**
     * Register an acknowledgment to be associated with the current Bedrock batch.
     */
    public void sendLatencyStack(Acknowledgment ack) {
        this.ackTransport.send(ack);
    }

    /**
     * Emit a keepalive ping (no acknowledgment). Used by {@code serverTick} for liveness
     * detection when the server is idle and not sending packets
     */
    public void sendLatencyStack() {
        this.ackTransport.keepalive();
    }

    /**
     * Register an acknowledgment to ride the next outbound bedrock batch's injected NSL. Identical
     * semantics to {@link #sendLatencyStack(Acknowledgment)} under the default batched transport.
     */
    public void queueAcknowledgment(Acknowledgment ack) {
        this.ackTransport.attach(ack);
    }

    public void dispatchAcknowledgment(Acknowledgment ack) {
        Boar.getInstance().getAcknowledgmentRegistry().dispatch(this, ack);
    }

    /**
     * Replay a Bedrock packet inbound as if the client had just sent it. Used to forward
     * an attack (or any other inbound packet) that was previously cancelled by a check.
     * The synthetic packet skips Boar's listener chain on its way downstream, so the
     * caller is responsible for any prior listener-side processing.
     */
    public void injectClientPacket(BedrockPacket packet) {
        if (this.packetInjector == null) {
            // A missing injector is a platform wiring bug — the packet a check approved for
            // replay is lost. Log it so the drop is visible, and release the caller's reference.
            Boar.debug("injectClientPacket: dropped " + packet.getClass().getSimpleName() + " for player " + getSession().name() + ": packet injector is null", Boar.DebugMessage.WARNING);
            ReferenceCountUtil.safeRelease(packet);
            return;
        }
        this.packetInjector.injectClientPacket(packet);
    }

    public boolean isMovementExempted() {
        try {
            if (this.session.hasPermission("boar.exempt")) {
                return true;
            }
        } catch (Exception e) {
            Boar.getInstance().getPlatform().logger().error(getSession().name() + ": permission check for boar.exempt threw", e);
        }

        return this.abilities.contains(Ability.MAY_FLY) || this.getFlagTracker().isFlying() || this.getFlagTracker().isWasFlying();
    }

    public boolean disableMitigations() {
        return this.disableMitigations;
    }

    public void kick(String reason) {
        if (this.disableMitigations) {
            return;
        }

        disconnect(reason);
    }

    private void disconnect(String reason) {
        this.session.disconnect(Boar.getInstance().getAlertManager().getPrefix() + " " + reason);
    }

    // Prediction related method
    public void tick() {
        this.followerArrivalClock.observe(this.inputArrivalNs, this.tick);
        this.getActiveEffects().entrySet().removeIf(filter -> {
            filter.getValue().tick();
            return filter.getValue().getDuration() == 0;
        });

        try {
            for (final EntityCache cache : this.compensatedWorld.getEntities().values()) {
                cache.getHorseJumpEffect().tick();
                final boolean localBoat = cache.isLocallySimulatedBoat();
                cache.setControlledBoatStep(null);
                cache.setFollowerTickStart(cache.getCurrent() == null ? null : cache.actorPose(cache.getCurrent().getPos()));
                if (cache.getCurrent() != null) {
                    if (localBoat && (this.vehicleData.ticks == 0 || this.vehicleData.needsVehicleRestart)) {
                        cache.prepareBoatControlTick();
                    }
                    final int interpolationSteps = cache.tickCurrent();
                    if (localBoat && interpolationSteps > 0) {
                        cache.setControlledBoatStep(new EntityCache.ControlledBoatStep(this.tick, cache.getCurrent().getPos().clone(),
                                cache.getCurrent().getBoundingBox(), cache.getActorPitch(), cache.getActorYaw()));
                    }
                }
            }
        } catch (Exception e) {
            Boar.getInstance().getPlatform().logger().error(getSession().name() + ": entity tick threw", e);
        }

        this.getItemUseTracker().preTick();
    }

    // AuthInput handles a trusted dismount first, then this runs. Normal entity interpolation (smooth movement) still runs earlier.
    public void tickFollowerHistories() {
        try {
            for (final EntityCache cache : this.compensatedWorld.getEntities().values()) {
                // Tick all boats, nearby other vehicles, and the vehicle we ride.
                // A vehicle we used to control also needs its control-end entry after a far dismount.
                // Other far histories resync when they come close.
                final FollowerHistory history = cache.getFollowerHistory();
                final boolean riding = this.vehicleData != null && this.vehicleData.vehicleRuntimeId == cache.getRuntimeId();
                // Whether we control this boat is decided after AuthInput's trusted dismount.
                if (cache.isLocallySimulatedBoat() && cache.getCurrent() != null) {
                    if (this.vehicleData.useDriverSwitchTick(this.tick)) {
                        this.tickDriverSwitch(cache);
                        continue;
                    }
                    if (this.vehicleData.ticks == 0 || this.vehicleData.needsVehicleRestart) {
                        cache.captureBoatControlStartPose(history);
                    }
                    cache.resetToLocalControl(history);
                    continue;
                }
                // Near means near the shown pose OR the newest server move. The shown pose alone is not enough: it only moves while the history ticks.
                // After a teleport into range it stays at the old place, the history never ticks, and the first tick (the mount) resyncs with a box
                // over the whole teleport path. In-game testing showed this for horse mounts soon after a long teleport.
                if (history != null && (riding || history.isLocallyPredicted() || cache.getVehicleType() == VehicleType.BOAT
                        || history.distanceSquared(this.position.x, this.position.y, this.position.z) <= FOLLOWER_TICK_DISTANCE_SQUARED
                        || history.latestMoveDistanceSquared(this.position.x, this.position.y, this.position.z) <= FOLLOWER_TICK_DISTANCE_SQUARED)) {
                    this.tickRemoteFollowerHistory(cache, history, false);
                }
            }
        } catch (Exception e) {
            Boar.getInstance().getPlatform().logger().error(getSession().name() + ": follower history tick threw", e);
        }
    }

    public void tickDriverSwitch(final EntityCache cache) {
        final FollowerHistory history = cache.getFollowerHistory();
        if (history != null) {
            this.tickRemoteFollowerHistory(cache, history, true);
        } else {
            cache.beginDriverSwitchTick(null);
        }
        cache.finishDriverSwitchTick(history);
    }

    private void tickRemoteFollowerHistory(final EntityCache cache, final FollowerHistory history, final boolean duringDriverSwitch) {
        if (cache.getVehicleType() == VehicleType.BOAT && cache.getCurrent() != null) {
            if (history.isLocallyPredicted()) {
                if (duringDriverSwitch) {
                    cache.beginDriverSwitchTick(history);
                }
                final FollowerHistory.ActorPose actor = cache.actorPose(cache.getCurrent().getPos());
                // The fallback keeps using the existing interpolator. Its forced-completion queue is not modeled yet.
                final var interpolator = cache.getCurrent().getInterpolator();
                final Vec3 target = interpolator.getTargetPos();
                final var angles = cache.getPendingServerAngles();
                final FollowerHistory.ActorPose pendingTarget = target == null ? null : new FollowerHistory.ActorPose(
                        target.x, target.y + cache.getYOffset(), target.z,
                        angles == null ? actor.pitch() : angles.pitch(),
                        angles == null ? actor.yaw() : angles.yaw(),
                        angles == null ? actor.headYaw() : angles.headYaw(),
                        actor.onGround());
                history.resumeRemoteTick(this.inputArrivalNs, this.getLatencyUtil().nowLowerBoundNs(), this.tick, cache.getFollowerTickStart(), actor,
                        pendingTarget, interpolator.getStep());
                return;
            }
        }
        if (history.hasTicked() && !history.tickedLast(this.tick)) {
            history.resync(this.inputArrivalNs, this.getLatencyUtil().nowLowerBoundNs());
        }
        // A resync can change the history's target and steps left (remaining). It runs before this normal step.
        if (duringDriverSwitch) {
            cache.beginDriverSwitchTick(history);
        }
        history.tick(this.inputArrivalNs, this.getLatencyUtil().nowLowerBoundNs(), this.tick);
    }

    public void postTick() {
        this.glideBoostTicks--; // Glide boost should tick regardless if the player is gliding or not!
        this.getItemUseTracker().postTick();
    }

    @Override
    public void setPos(final Vec3 vec3, final boolean prev) {
        super.setPos(vec3, prev);
        this.nativeOriginY = this.getYOffset() + vec3.y; // done in FinalizeMoveSystemImpl::tickFinalizeMoveSystem
    }

    public float getYOffset() {
        if (this.vehicleData != null) {
            if (this.vehicleData.type == VehicleType.BOAT) {
                return VehicleType.BOAT_OFFSET;
            }

            return 0;
        }

        return this.getPlayerYOffset();
    }

    // MovePlayer, Respawn and ChangeDimension positions for ourselves use this Y offset, even while riding.
    public float getPlayerYOffset() {
        return EntityDefinitions.PLAYER.find().map(EntityDefinition::offset).orElse(0.0f);
    }

    public float getFrictionInfluencedSpeed(float slipperiness) {
        if (this.onGround) {
            // GroundTravelTypeSystem::calcMoveRelativeSpeed - soul sand (without Soul Speed) only raises the friction used here, not the friction that slows velocity down at the end of the tick.
            float friction = slipperiness;
            if (this.compensatedWorld.getBlockState(this.getBlockPosBelowThatAffectsMyMovement(), 0).is(Blocks.SOUL_SAND) &&
                    !CompensatedInventory.getEnchantments(this.compensatedInventory.armorContainer.get(3).getData()).containsKey(Enchantment.SOUL_SPEED)) {
                friction *= 1.225F;
            }

            float f = friction * 0.91F;
            if (f == 0.0F) {
                f = 0.54600006F;
            }
            final float ratio = 0.54600006F / f;
            return ratio * ratio * ratio * this.getSpeed();
        }

        return this.getFlagTracker().has(EntityFlag.SPRINTING) ? 0.026F : 0.02F;
    }

    public BoarBlockState getInBlockState() {
        if (this.inBlockState == null) {
            this.inBlockState = this.compensatedWorld.getBlockState(this.position.toVector3i(), 0);
        }

        return this.inBlockState;
    }

    public boolean onClimbable() {
        return BlockMappings.get().getClimbableBlocks().contains(this.getInBlockState().block());
    }

    public float getJumpPower() {
        return PlayerData.JUMP_HEIGHT * this.getBlockJumpFactor() + this.getJumpBoostPower();
    }

    public float getJumpBoostPower() {
        return this.hasEffect(Effect.JUMP_BOOST) ? 0.1F * (this.getActiveEffects().get(Effect.JUMP_BOOST).getAmplifier() + 1.0F) : 0.0F;
    }

    public Vec3 jump(Vec3 vec3) {
        // (https://mojang.github.io/bedrock-protocol-docs/html/enums.html)
        // AutoJumpingInWater - "If an auto jump is currently triggering while touching water. Can be ignored if handling Jumping properly"
        // But they don't even send JUMPING input data if the player is auto jumping, nice job there mojang.
        // Matter of fact, this does not only apply to water, but lava as well! really misleading docs lol.
        boolean autoJumping = this.getInputData().contains(PlayerAuthInputData.AUTO_JUMPING_IN_WATER);
        boolean jumping = this.getInputData().contains(PlayerAuthInputData.JUMPING);

        boolean canJumpInWater = this.selectedFluid == Fluid.WATER, canJumpInLava = this.selectedFluid == Fluid.LAVA;
        if ((jumping || autoJumping) && (canJumpInWater || canJumpInLava)) {
            final boolean swimmingWithDryHead = this.getFlagTracker().has(EntityFlag.SWIMMING) && !this.headInWater;
            final boolean partialSwimAmount = this.swimAmount > 0 && this.swimAmount < 1;
            if (swimmingWithDryHead || partialSwimAmount) {
                this.getMovementTrace().log("liquid jump: swim-state return, dryHead=" + swimmingWithDryHead + " partialAmount=" + partialSwimAmount + " selected=" + this.selectedFluid);
                return canJumpInWater ? new Vec3(vec3.x, 0, vec3.z) : vec3; // MobJumpSystem::doMobJumpSystem
            }
            vec3 = vec3.add(0, 0.04F, 0);
        } else if (this.onGround && this.getInputData().contains(PlayerAuthInputData.START_JUMPING)) {
            vec3 = this.jumpFromGround(vec3);
        }

        return vec3;
    }

    private Vec3 jumpFromGround(Vec3 vec3) {
        float f = this.getJumpPower();
        if (f <= 1.0E-5f) {
            return vec3;
        }
        vec3 = new Vec3(vec3.x, Math.max(f, vec3.y), vec3.z);
        if (this.getFlagTracker().has(EntityFlag.SPRINTING)) {
            float g = this.yaw * MathUtil.DEGREE_TO_RAD;
            vec3 = vec3.add(-TrigMath.sin(g) * 0.2F, 0, TrigMath.cos(g) * 0.2F);
        }

        return vec3;
    }

    public float getBlockJumpFactor() {
        float f = this.compensatedWorld.getBlockState(this.position.toVector3i(), 0).getJumpFactor();
        float g = this.compensatedWorld.getBlockState(this.getBlockPosBelowThatAffectsMyMovement(), 0).getJumpFactor();
        return f == 1.0 ? g : f;
    }

    public Vector3i getBlockPosBelowThatAffectsMyMovement() {
        // This is correct, not getOnPos, try moving on the edge of the slime block on JE/BE and you will see the difference.
        return position.down(0.1F).toVector3i();
    }

    public Vector3i cachedOnPos;
    public Vector3i getOnPos(final float offset) {
        if (this.cachedOnPos == null) {
            Vector3i blockPos = null;
            float d = Float.MAX_VALUE;

            final CuboidBlockIterator iterator = CuboidBlockIterator.iterator(boundingBox.expand(1.0E-3F));
            while (iterator.step()) {
                int x = iterator.getX(), y = iterator.getY(), z = iterator.getZ();
                Vector3i blockPos2 = Vector3i.from(x, y, z);
                if (compensatedWorld.getBlockState(x, y, z, 0).findCollision(this, Vector3i.from(x, y, z), null, false).isEmpty()) {
                    continue;
                }

                float e = new Vec3(blockPos2).distToCenterSqr(this.position);

                if (e < d || e == d && (blockPos == null || new Vec3(blockPos).compareTo(blockPos2) < 0)) {
                    blockPos = blockPos2;
                    d = e;
                }
            }

            if (blockPos != null) {
                this.cachedOnPos = blockPos;
            } else {
                this.cachedOnPos = this.position.toVector3i();
            }
        }

        return Vector3i.from(this.cachedOnPos.getX(), GenericMath.floor(this.position.y - offset), this.cachedOnPos.getZ());
    }

    public boolean isRegionUnloaded() {
        final Box lv = this.boundingBox.expand(1);
        int i = GenericMath.floor(lv.minX);
        int j = GenericMath.ceil(lv.maxX);
        int k = GenericMath.floor(lv.minZ);
        int l = GenericMath.ceil(lv.maxZ);
        return !this.compensatedWorld.hasChunksAt(i, k, j, l);
    }

    public boolean containsFluid(Box box) {
        int i = GenericMath.floor(box.minX);
        int j = GenericMath.ceil(box.maxX);
        int k = GenericMath.floor(box.minY);
        int l = GenericMath.ceil(box.maxY);
        int m = GenericMath.floor(box.minZ);
        int n = GenericMath.ceil(box.maxZ);
        Mutable lv = new Mutable();

        for (int o = i; o < j; o++) {
            for (int p = k; p < l; p++) {
                for (int q = m; q < n; q++) {
                    lv.set(o, p, q);
                    FluidState lv2 = this.compensatedWorld.getFluidState(lv);
                    if (lv2.fluid() != Fluid.EMPTY) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean doesNotCollide(float offsetX, float offsetY, float offsetZ) {
        return this.doesNotCollide(this.boundingBox.offset(offsetX, offsetY, offsetZ));
    }

    private boolean doesNotCollide(Box box) {
        return this.compensatedWorld.noCollision(box) && !containsFluid(box);
    }
}
