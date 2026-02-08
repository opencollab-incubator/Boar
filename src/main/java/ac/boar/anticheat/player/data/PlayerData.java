package ac.boar.anticheat.player.data;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.data.*;
import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.data.vanilla.StatusEffect;
import ac.boar.anticheat.player.data.tracker.FlagTracker;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeModifierData;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeOperation;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.inventory.item.BedrockEnchantment;
import org.geysermc.geyser.level.block.Fluid;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerData {
    private final static AttributeModifierData SPRINTING_SPEED_BOOST = new AttributeModifierData("D208FC00-42AA-4AAD-9276-D5446530DE43",
            "Sprinting speed boost",
            0.3F, AttributeOperation.MULTIPLY_TOTAL, 2, false);

    public final static float JUMP_HEIGHT = 0.42F;
    public final static float STEP_HEIGHT = 0.6F;
    public final static float GRAVITY = 0.08F;

    // Geyser Mappings related
    public int BEDROCK_AIR = -1;
    public List<Integer> AIR_IDS = new ArrayList<>();
    public final Map<Integer, Integer> bedrockBlockToJava = new HashMap<>();

    @Getter
    @Setter
    private Set<PlayerAuthInputData> inputData = new HashSet<>();

    public long tick = -1; // Allow tick id 0.
    public long sinceAuthInput = System.currentTimeMillis();

    public Integer currentLoadingScreen = null;
    public boolean inLoadingScreen;
    public int sinceLoadingScreen;

    public boolean insideUnloadedChunk;

    public GameType gameType = GameType.DEFAULT;
    public InputMode inputMode = InputMode.UNDEFINED;

    // Position, rotation, other.
    public float yaw, pitch, prevYaw, prevPitch;
    public Vec3 unvalidatedPosition = Vec3.ZERO, prevUnvalidatedPosition = Vec3.ZERO;
    public Vector2f interactRotation = Vector2f.ZERO;

    public Vec3 position = Vec3.ZERO, prevPosition = Vec3.ZERO;
    public Vector3f rotation = Vector3f.ZERO;

    // Sprinting, sneaking, swimming and other status.
    @Getter
    private final FlagTracker flagTracker = new FlagTracker();

    public int glideBoostTicks;
    public int ticksSinceSwimming, ticksSinceCrawling;

    public boolean doingInventoryAction;
    public AtomicLong desyncedFlag = new AtomicLong(-1);

    // Effect status related
    @Getter
    private final Map<Effect, StatusEffect> activeEffects = new ConcurrentHashMap<>();
    public boolean hasEffect(final Effect effect) {
        return this.activeEffects.containsKey(effect);
    }
    public StatusEffect getEffect(final Effect effect) {
        return this.activeEffects.get(effect);
    }

    // Movement related, (movement input, player EOT, ...)
    public Vec3 input = Vec3.ZERO;
    public Vec3 unvalidatedTickEnd = Vec3.ZERO;

    public Vector uncertainVelocity, certainVelocity;

    // Attribute related, abilities
    public final Map<String, AttributeInstance> attributes = new HashMap<>();
    public final Set<Ability> abilities = new HashSet<>();

    // Riptide related
    public boolean dirtyRiptide, dirtySpinStop, thisTickSpinAttack, thisTickOnGroundSpinAttack;
    public int autoSpinAttackTicks, sinceTridentUse;
    public ItemData riptideItem = ItemData.AIR;
    public void setDirtyRiptide(int j, ItemData data) {
        if (j < 10 || !CompensatedInventory.getEnchantments(data).containsKey(BedrockEnchantment.RIPTIDE)) {
            return;
        }

        this.riptideItem = data;
        this.dirtyRiptide = true;
    }
    public void stopRiptide() {
        this.dirtyRiptide = this.dirtySpinStop = false;
        this.autoSpinAttackTicks = 0;
        this.riptideItem = ItemData.AIR;

        this.getFlagTracker().set(EntityFlag.DAMAGE_NEARBY_MOBS, false);
    }

    // Prediction related
    public EntityDimensions dimensions = EntityDimensions.changing(0.6F, 1.8F).withEyeHeight(1.62F);
    public Box boundingBox = Box.EMPTY;

    public Vec3 velocity = Vec3.ZERO, lastTickFinalVelocity = Vec3.ZERO;

    public PredictionData predictionResult = new PredictionData(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
    public Vector bestPossibility = Vector.NONE;
    public Vec3 beforeCollision = Vec3.ZERO, afterCollision = Vec3.ZERO;

    public boolean onGround;
    public Vec3 stuckSpeedMultiplier = Vec3.ZERO;

    public float fallDistance = 0;

    public boolean hasDepthStrider;
    public boolean touchingWater;
    public boolean horizontalCollision, verticalCollision;
    public boolean soulSandBelow;

    public boolean nearBamboo;

    public boolean beingPushByLava;

    public final Map<Fluid, Float> fluidHeight = new HashMap<>();
    public float getFluidHeight(Fluid tagKey) {
        return this.fluidHeight.getOrDefault(tagKey, 0F);
    }

    public BlockState inBlockState;
    public boolean scaffoldDescend;

    public VehicleData vehicleData = null;

    public int tickSinceBlockResync;

    // Prediction related method
    public final float getMaxOffset() {
        return Boar.getConfig().acceptanceThreshold();
    }

    public final void setSprinting(boolean sprinting) {
        this.getFlagTracker().set(EntityFlag.SPRINTING, sprinting);
        final AttributeInstance lv = this.attributes.get(GeyserAttributeType.MOVEMENT_SPEED.getBedrockIdentifier());
        if (lv == null) {
            // wtf?
            return;
        }

        lv.removeModifier(SPRINTING_SPEED_BOOST.getId());
        if (sprinting) {
            lv.addTemporaryModifier(SPRINTING_SPEED_BOOST);
        }
    }

    public boolean isInLava() {
        return this.tick != 1 && this.fluidHeight.getOrDefault(Fluid.LAVA, 0F) != 0.0;
    }

    public final float getEffectiveGravity(final Vec3 vec3) {
        return vec3.y < 0.0 && this.hasEffect(Effect.SLOW_FALLING) ? Math.min(GRAVITY, 0.01F) : GRAVITY;
    }

    public final float getEffectiveGravity() {
        return this.getEffectiveGravity(this.velocity);
    }

    public float getSpeed() {
        return this.attributes.get(GeyserAttributeType.MOVEMENT_SPEED.getBedrockIdentifier()).getValue();
    }

    // Others (methods)
    public final void setPos(Vec3 vec3) {
        this.setPos(vec3, true);
    }

    public final void setPos(Vec3 vec3, boolean prev) {
        if (prev) {
            this.prevPosition = this.position;
        }

        this.position = vec3;
        if (this.vehicleData != null) {
            return;
        }

        this.setBoundingBox(vec3);

        this.inBlockState = null;
    }

    public final void setBoundingBox(Vec3 vec3) {
        this.boundingBox = this.dimensions.getBoxAt(vec3.x, vec3.y, vec3.z);
    }
}
