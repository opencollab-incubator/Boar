package ac.boar.anticheat.player;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.collision.util.CuboidBlockIterator;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.compensated.world.CompensatedWorldImpl;
import ac.boar.anticheat.data.ItemUseTracker;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.teleport.TeleportUtil;
import ac.boar.anticheat.util.LatencyUtil;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.anticheat.validator.blockbreak.ServerBreakBlockValidator;
import ac.boar.mappings.BlockMappings;
import ac.boar.protocol.BoarHandlerAdaptor;
import io.netty.channel.EventLoop;
import lombok.Getter;

import ac.boar.anticheat.check.api.holder.CheckHolder;
import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.anticheat.validator.inventory.ItemTransactionValidator;
import ac.boar.anticheat.player.data.PlayerData;
import lombok.Setter;
import lombok.SneakyThrows;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.TrigMath;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakSessionCodec;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.inventory.item.BedrockEnchantment;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.Fluid;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class BoarPlayer extends PlayerData {
    @Getter
    private final GeyserSession session;
    @Getter
    @Setter
    private BedrockServerSession bedrockSession;

    public long runtimeEntityId;

    @Getter
    private final TeleportUtil teleportUtil = new TeleportUtil(this);

    @Getter
    private final CheckHolder checkHolder = new CheckHolder(this);

    @Getter
    private final LatencyUtil latencyUtil = new LatencyUtil(this);

    // Lag compensation
    public final CompensatedWorldImpl compensatedWorld = new CompensatedWorldImpl(this);
    public final CompensatedInventory compensatedInventory = new CompensatedInventory(this);

    // Validation
    public ServerBreakBlockValidator serverBreakBlockValidator;
    public final ItemTransactionValidator transactionValidator = new ItemTransactionValidator(this);

    @Getter
    private final ItemUseTracker itemUseTracker = new ItemUseTracker(this);

    @Getter
    private final Map<UUID, CommandSource> trackedDebugPlayers = new ConcurrentHashMap<>();

    @SneakyThrows
    public BoarPlayer(GeyserSession session) {
        this.session = session;

        org.geysermc.geyser.registry.type.BlockMappings mappings = session.getBlockMappings();
        for (int i = 0; i < mappings.getJavaToBedrockBlocks().length; i++) {
            this.bedrockBlockToJava.put(mappings.getJavaToBedrockBlocks()[i].getRuntimeId(), i);
        }

        BEDROCK_AIR = mappings.getBedrockAir().getRuntimeId();
        AIR_IDS.add(BEDROCK_AIR);
        AIR_IDS.add(mappings.getBedrockBlockId(Blocks.CAVE_AIR.defaultBlockState().javaId()));
        AIR_IDS.add(mappings.getBedrockBlockId(Blocks.VOID_AIR.defaultBlockState().javaId()));

        for (GeyserAttributeType type : GeyserAttributeType.values()) {
            final String identifier = type.getBedrockIdentifier();
            if (identifier == null || this.attributes.containsKey(type.getBedrockIdentifier())) {
                continue;
            }

            this.attributes.put(identifier, new AttributeInstance(type.getDefaultValue()));
        }


        final Field field = GeyserSession.class.getDeclaredField("tickEventLoop");
        field.setAccessible(true);
        ((EventLoop)field.get(session)).scheduleAtFixedRate(this::serverTick, 50000000, 50000000, TimeUnit.NANOSECONDS);
    }

    public void serverTick() {
        if (this.getLatencyUtil().sentQueue().isEmpty()) {
            sendLatencyStack();
            return;
        }

        if (System.currentTimeMillis() - this.getLatencyUtil().prevAcceptedTime > Boar.getConfig().maxLatencyWait()) {
            kick("Timed out!");
        }
    }

    public boolean isClosed() {
        return this.session.isClosed() || this.session.getUpstream().isClosed();
    }

    public void sendLatencyStack(Runnable runnable) {
        sendLatencyStack();
        this.latencyUtil.queue(runnable);
    }

    public void sendLatencyStack() {
        long id = ThreadLocalRandom.current().nextLong(-5000000L, 5000000L);

        final NetworkStackLatencyPacket latencyPacket = new NetworkStackLatencyPacket();
        latencyPacket.setTimestamp(id);
        latencyPacket.setFromServer(true);

        this.bedrockSession.getPeer().getChannel().pipeline().context(BoarHandlerAdaptor.NAME).writeAndFlush(
                BedrockPacketWrapper.create(0, 0, 0, latencyPacket, null)
        );

        this.latencyUtil.queue(id, true);
    }

    public boolean isMovementExempted() {
        try { // Ye, well whatever.
            if (this.session.hasPermission("boar.exempt")) {
                return true;
            }
        } catch (Exception ignored) {}

        return this.abilities.contains(Ability.MAY_FLY) || this.getFlagTracker().isFlying() || this.getFlagTracker().isWasFlying();
    }

    public void kick(String reason) {
        this.session.disconnect(Boar.getInstance().getAlertManager().getPrefix(getSession()) + " " + reason);
    }

    // Prediction related method
    public void tick() {
        this.getActiveEffects().entrySet().removeIf(filter -> {
            filter.getValue().tick();
            return filter.getValue().getDuration() == 0;
        });

        try {
            for (final EntityCache cache : this.compensatedWorld.getEntities().values()) {
                if (cache.getPast() != null) {
                    cache.getPast().tick();
                }

                if (cache.getCurrent() != null) {
                    cache.getCurrent().tick();
                }
            }
        } catch (Exception ignored) {}

        this.getItemUseTracker().preTick();
    }

    public void postTick() {
        this.glideBoostTicks--;
        this.getItemUseTracker().postTick();
    }

    public float getYOffset() {
        if (this.vehicleData != null) {
            final EntityCache cache = this.compensatedWorld.getEntity(this.vehicleData.vehicleRuntimeId);
            if (cache != null) {
                final String identifier = cache.getDefinition().identifier();

                if (identifier.equals("minecraft:boat") || identifier.equals("minecraft:chest_boat")) {
                    return EntityDefinitions.BIRCH_BOAT.offset(); // It's all the same anyway, I just like birch :)
                }
            }

            return 0;
        }

        return EntityDefinitions.PLAYER.offset();
    }

    public float getFrictionInfluencedSpeed(float slipperiness) {
        if (this.onGround) {
            float speed = this.getSpeed() * (0.21600002F / (slipperiness * slipperiness * slipperiness));
            if (!CompensatedInventory.getEnchantments(this.compensatedInventory.armorContainer.get(3).getData()).containsKey(BedrockEnchantment.SOUL_SPEED) && this.soulSandBelow) {
                speed *= 0.55F; // not accurate, but well I can just give extra offset if player movement is slower than the predicted one.
            }

            return speed;
        }

        return this.getFlagTracker().has(EntityFlag.SPRINTING) ? 0.026F : 0.02F;
    }

    public BlockState getInBlockState() {
        if (this.inBlockState == null) {
            this.inBlockState = this.compensatedWorld.getBlockState(this.position.toVector3i(), 0).getState();
        }

        return this.inBlockState;
    }

    public boolean onClimbable() {
        return BlockMappings.getClimbableBlocks().contains(this.getInBlockState().block());
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
