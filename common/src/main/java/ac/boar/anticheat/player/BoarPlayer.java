package ac.boar.anticheat.player;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.ack.BoarAcknowledgmentTransport;
import ac.boar.anticheat.ack.BoarBatchedAcknowledgmentTransport;
import ac.boar.anticheat.check.api.holder.CheckHolder;
import ac.boar.anticheat.collision.util.CuboidBlockIterator;
import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.compensated.world.CompensatedWorldImpl;
import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.data.ItemUseTracker;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.data.enchantment.Enchantment;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.player.accessor.EntityAccessor;
import ac.boar.anticheat.player.accessor.InventoryAccessor;
import ac.boar.anticheat.player.accessor.WorldAccessor;
import ac.boar.anticheat.player.data.BlockMappingInfo;
import ac.boar.anticheat.player.data.PlayerData;
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
import ac.boar.protocol.PacketInjector;
import io.netty.util.ReferenceCountUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.TrigMath;
import org.cloudburstmc.math.vector.Vector3i;
import ac.boar.protocol.BoarConnection;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

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

    public long runtimeEntityId;

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
        this.getActiveEffects().entrySet().removeIf(filter -> {
            filter.getValue().tick();
            return filter.getValue().getDuration() == 0;
        });

        try {
            for (final EntityCache cache : this.compensatedWorld.getEntities().values()) {
                if (cache.getCurrent() != null) {
                    cache.getCurrent().tick();
                }
            }
        } catch (Exception e) {
            Boar.getInstance().getPlatform().logger().error(getSession().name() + ": entity tick threw", e);
        }

        this.getItemUseTracker().preTick();
    }

    public void postTick() {
        this.glideBoostTicks--; // Glide boost should tick regardless if the player is gliding or not!
        this.getItemUseTracker().postTick();
    }

    public float getYOffset() {
        if (this.vehicleData != null) {
            final EntityCache cache = this.compensatedWorld.getEntity(this.vehicleData.vehicleRuntimeId);
            if (cache != null) {
                final String identifier = cache.getDefinition().identifier();

                if (identifier.equals("minecraft:boat") || identifier.equals("minecraft:chest_boat")) {
                    return EntityDefinitions.BIRCH_BOAT.find().map(EntityDefinition::offset).orElse(0.0f); // It's all the same anyway, I just like birch :)
                }
            }

            return 0;
        }

        return EntityDefinitions.PLAYER.find().map(EntityDefinition::offset).orElse(0.0f);
    }

    public float getFrictionInfluencedSpeed(float slipperiness) {
        if (this.onGround) {
            float speed = this.getSpeed() * (0.21600002F / (slipperiness * slipperiness * slipperiness));
            if (!CompensatedInventory.getEnchantments(this.compensatedInventory.armorContainer.get(3).getData()).containsKey(Enchantment.SOUL_SPEED) && this.soulSandBelow) {
                speed *= 0.55F; // not accurate, but well I can just give extra offset if player movement is slower than the predicted one.
            }

            return speed;
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

        boolean canJumpInWater = this.getFluidHeight(Fluid.WATER) != 0, canJumpInLava = this.isInLava();
        if ((jumping || autoJumping) && (canJumpInWater || canJumpInLava)) {
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
