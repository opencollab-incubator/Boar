package ac.boar.anticheat.player.data;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.data.vanilla.Attribute;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.data.enchantment.Enchantment;
import ac.boar.anticheat.data.input.PredictionResult;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.data.vanilla.StatusEffect;
import ac.boar.anticheat.player.data.tracker.FlagTracker;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeModifierData;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeOperation;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RequiredArgsConstructor
public class PlayerData {
    private static final float SPRINTING_SPEED_MULTIPLIER = 1.3F;

    public record SprintContext(
            boolean startEdge,
            boolean stopEdge,
            boolean serverFlagPending,
            boolean uncertain,
            boolean assumedSprinting,
            float preUpdateMovementSpeed,
            boolean serverSpeedUpdateActive
    ) {}

    public record SneakContext(
            boolean startEdge,
            boolean stopEdge,
            boolean serverFlagPending,
            boolean uncertain,
            boolean assumedSneaking
    ) {}

    public final static float JUMP_HEIGHT = 0.42F;
    public final static float STEP_HEIGHT = 0.6F;
    public final static float GRAVITY = 0.08F;

    // Mappings related
    public final BlockMappingInfo mappingInfo;

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
    public InputInteractionModel interactionModel = InputInteractionModel.TOUCH;

    // Position, rotation, other.
    public float yaw, pitch, prevYaw, prevPitch;
    public Vec3 unvalidatedPosition = Vec3.ZERO, prevUnvalidatedPosition = Vec3.ZERO;

    public Vector2f interactRotation = Vector2f.ZERO, prevInteractRotation = Vector2f.ZERO;
    public boolean prevInteractRotUnchanged = false;

    public Vec3 position = Vec3.ZERO, prevPosition = Vec3.ZERO;
    public Vector3f rotation = Vector3f.ZERO;

    // Sprinting, sneaking, swimming and other status.
    @Getter
    private final FlagTracker flagTracker = new FlagTracker();

    public float sneakingAttributeModifier;

    public int glideBoostTicks;
    public int ticksSinceSwimming, ticksSinceCrawling, ticksSinceCanSlowdown;

    @Getter
    boolean serverSprinting;
    @Getter
    boolean serverSprintingApplied = true;
    public SprintContext sprintContext;

    @Getter
    boolean serverSneaking;
    @Getter
    boolean serverSneakingApplied = true;
    public SneakContext sneakContext;

    @Getter
    public boolean serverUpdatedMovementSpeed;
    public Float pendingServerMovementSpeed;
    public Float airSpeedOverride;

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

    public Vector certainVelocity;

    // Attribute related, abilities
    public final Map<String, AttributeInstance> attributes = new HashMap<>();
    public final Set<Ability> abilities = new HashSet<>();

    // Riptide related
    public boolean dirtyRiptide, dirtySpinStop, thisTickSpinAttack, thisTickOnGroundSpinAttack;
    public int autoSpinAttackTicks, sinceTridentUse;
    public ItemData riptideItem = ItemData.AIR;
    public void setDirtyRiptide(int j, ItemData data) {
        if (j < 10 || !CompensatedInventory.getEnchantments(data).containsKey(Enchantment.RIPTIDE)) {
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

    public PredictionResult predictionResult = new PredictionResult(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
    public Vector bestPossibility = Vector.NONE;
    public Vec3 beforeCollision = Vec3.ZERO, afterCollision = Vec3.ZERO;

    public boolean onGround;
    public boolean bounce;
    public float minBounceYVel;

    public Vec3 stuckSpeedMultiplier = Vec3.ZERO;

    public float fallDistance = 0;

    public boolean hasDepthStrider;
    public boolean touchingWater;
    public boolean horizontalCollision, verticalCollision;
    public boolean stuckInCollider, penetratedLastFrame;
    public boolean soulSandBelow;

    public boolean nearBamboo;

    public boolean beingPushByLava;

    public final Map<Fluid, Float> fluidHeight = new HashMap<>();
    public float getFluidHeight(Fluid tagKey) {
        return this.fluidHeight.getOrDefault(tagKey, 0F);
    }

    public BoarBlockState inBlockState;
    public Vector3i cachedOnPos;
    public boolean scaffoldDescend;

    public VehicleData vehicleData = null;

    public Vector3i bedPosition = null;

    public int tickSinceBlockResync;

    // Prediction related method
    public final float getMaxOffset() {
        return Boar.getConfig().acceptanceThreshold();
    }

    // Headroom (in ULPs per axis) above the raw representational granularity, to absorb the extra
    // float rounding that accumulates across a tick's worth of movement math on both client and server.
    private static final float POSITION_PRECISION_FACTOR = 2.0F;

    // Acceptance window for a server-vs-client *position* difference.
    //
    // World coordinates are stored as 32-bit floats (see Vec3), whose granularity grows with magnitude:
    // Math.ulp(100000f) is already ~0.0078, an order of magnitude past the 0.001 base. A flat threshold
    // would therefore false-correct legitimate players the further they get from the origin. So we widen
    // the window by the combined per-axis representational error, and never go below getMaxOffset().
    //
    // Only position differences need this - velocity/delta comparisons stay on getMaxOffset() since their
    // magnitude is tiny regardless of where in the world the player is.
    public final float getPositionOffset() {
        final float ulpX = Math.ulp(Math.max(Math.abs(position.x), Math.abs(unvalidatedPosition.x)));
        final float ulpY = Math.ulp(Math.max(Math.abs(position.y), Math.abs(unvalidatedPosition.y)));
        final float ulpZ = Math.ulp(Math.max(Math.abs(position.z), Math.abs(unvalidatedPosition.z)));
        final float precision = (float) Math.sqrt(ulpX * ulpX + ulpY * ulpY + ulpZ * ulpZ) * POSITION_PRECISION_FACTOR;
        return Math.max(getMaxOffset(), precision);
    }

    public final void setSprinting(boolean sprinting) {
        this.getFlagTracker().set(EntityFlag.SPRINTING, sprinting);
    }

    public final void setServerSprinting(boolean sprinting) {
        this.serverSprinting = sprinting;
        this.serverSprintingApplied = false;
    }

    public final void updateSprintingState(boolean startSprinting, boolean stopSprinting) {
        final boolean serverFlagPending = !this.serverSprintingApplied;
        final boolean currentSprinting = this.getFlagTracker().has(EntityFlag.SPRINTING);
        final boolean assumedSprinting;
        if (startSprinting && stopSprinting) {
            assumedSprinting = false;
        } else if (!startSprinting && !stopSprinting && serverFlagPending && this.serverSprinting != currentSprinting) {
            assumedSprinting = this.serverSprinting;
        } else if (startSprinting) {
            assumedSprinting = true;
        } else if (stopSprinting) {
            assumedSprinting = false;
        } else {
            assumedSprinting = currentSprinting;
        }
        final AttributeInstance movement = this.attributes.get(Attribute.MOVEMENT.getIdentifier());
        final float preUpdateMovementSpeed = movement != null ? movement.getValue() : 0.0F;
        final boolean uncertain = this.isSprintTransitionUncertain(startSprinting, stopSprinting, serverFlagPending, currentSprinting);
        this.sprintContext = new SprintContext(
                startSprinting, stopSprinting, serverFlagPending,
                uncertain, assumedSprinting,
                preUpdateMovementSpeed, this.serverUpdatedMovementSpeed
        );

        boolean needsSpeedAdjusted = false;

        if (startSprinting && stopSprinting) {
            this.setSprinting(false);
            needsSpeedAdjusted = true;
        } else if (!startSprinting && !stopSprinting && !this.serverSprintingApplied && this.serverSprinting != this.getFlagTracker().has(EntityFlag.SPRINTING)) {
            this.setSprinting(this.serverSprinting);
        } else if (startSprinting) {
            this.setSprinting(true);
            needsSpeedAdjusted = true;
        } else if (stopSprinting) {
            this.setSprinting(false);
            if (this.input.z > 0.0F) {
                needsSpeedAdjusted = !this.serverUpdatedMovementSpeed;
            }
        }

        this.serverSprintingApplied = true;

        if (needsSpeedAdjusted) {
            this.updateMovementSpeedFromSprinting();
        }
    }

    private boolean isSprintTransitionUncertain(final boolean startSprinting, final boolean stopSprinting, final boolean serverFlagPending, final boolean currentSprinting) {
        final boolean pendingConflicts = serverFlagPending && this.serverSprinting != currentSprinting;

        if (startSprinting || stopSprinting) {
            final boolean speedDerivable = this.sprintSpeedLocallyDerivable();
            final boolean cleanStartEdge = startSprinting && !stopSprinting && this.input.z > 0.0F && speedDerivable;
            final boolean cleanStopEdge = stopSprinting && !startSprinting && this.input.z <= 0.0F && speedDerivable;
            return !((cleanStartEdge || cleanStopEdge) && !pendingConflicts);
        }

        return pendingConflicts;
    }

    public final void setSneaking(boolean sneaking) {
        this.flagTracker.set(EntityFlag.SNEAKING, sneaking);
    }

    public final void updateSneakingState(boolean startSneak, boolean stopSneak, boolean sneakingInput) {
        final boolean serverFlagPending = !this.serverSneakingApplied;
        final boolean currentSneaking = this.flagTracker.has(EntityFlag.SNEAKING);

        final boolean assumedSneaking;
        if (!startSneak && !stopSneak && serverFlagPending && this.serverSneaking != currentSneaking) {
            assumedSneaking = this.serverSneaking;
        } else {
            assumedSneaking = sneakingInput;
        }

        final boolean uncertain = this.isSneakTransitionUncertain(startSneak, stopSneak, sneakingInput, serverFlagPending, currentSneaking, assumedSneaking);
        this.sneakContext = new SneakContext(startSneak, stopSneak, serverFlagPending, uncertain, assumedSneaking);

        this.setSneaking(assumedSneaking);
        this.serverSneakingApplied = true;
    }

    private boolean isSneakTransitionUncertain(
            final boolean startSneak,
            final boolean stopSneak,
            final boolean sneakingInput,
            final boolean serverFlagPending,
            final boolean currentSneaking,
            final boolean assumedSneaking
    ) {
        final boolean pendingConflicts = serverFlagPending && this.serverSneaking != currentSneaking;
        if (startSneak || stopSneak) {
            final boolean cleanStartEdge = startSneak && !stopSneak && sneakingInput;
            final boolean cleanStopEdge = stopSneak && !startSneak && !sneakingInput;
            return !((cleanStartEdge || cleanStopEdge) && !pendingConflicts);
        }

        return pendingConflicts || assumedSneaking != currentSneaking;
    }

    private boolean sprintSpeedLocallyDerivable() {
        final AttributeInstance attribute = this.attributes.get(Attribute.MOVEMENT.getIdentifier());
        if (attribute == null) {
            return false;
        }
        if (attribute.getModifiers().isEmpty()) {
            return true;
        }

        for (final AttributeModifierData modifier : attribute.getModifiers().values()) {
            if (modifier.getOperation() != AttributeOperation.ADDITION) {
                return false;
            }
        }

        final float addSum = this.movementAdditionModifierSum();
        final float cleanBase = attribute.getNonModifiedBaseValue();
        return addSum > 0.0F && cleanBase > 0.0F
                && Math.abs(addSum - cleanBase * (SPRINTING_SPEED_MULTIPLIER - 1.0F)) < 1.0E-5F;
    }

    public float deriveMovementSpeed(boolean sprinting) {
        final AttributeInstance attribute = this.attributes.get(Attribute.MOVEMENT.getIdentifier());
        if (attribute == null) {
            return 0.0F;
        }

        final float addSum = this.movementAdditionModifierSum();
        final float cleanBase = attribute.getNonModifiedBaseValue();
        if (addSum > 0.0F && cleanBase > 0.0F
                && Math.abs(addSum - cleanBase * (SPRINTING_SPEED_MULTIPLIER - 1.0F)) < 1.0E-5F) {
            return sprinting ? attribute.getBaseValue() : cleanBase;
        }

        float movementSpeed = attribute.getBaseValue();
        if (sprinting) {
            movementSpeed *= SPRINTING_SPEED_MULTIPLIER;
        }

        return movementSpeed;
    }

    public float movementAdditionModifierSum() {
        final AttributeInstance attribute = this.attributes.get(Attribute.MOVEMENT.getIdentifier());
        if (attribute == null) {
            return 0.0F;
        }

        float sum = 0.0F;
        for (final AttributeModifierData modifier : attribute.getModifiers().values()) {
            if (modifier.getOperation() == AttributeOperation.ADDITION) {
                sum += modifier.getAmount();
            }
        }
        return sum;
    }

    public Float movementSpeedWithoutAdditions() {
        final AttributeInstance attribute = this.attributes.get(Attribute.MOVEMENT.getIdentifier());
        if (attribute == null || attribute.getModifiers().isEmpty()) {
            return null;
        }

        final float sum = this.movementAdditionModifierSum();
        if (sum <= 0.0F) {
            return null;
        }

        final float withoutAdditions = attribute.getNonModifiedBaseValue();
        return withoutAdditions > 0.0F ? withoutAdditions : null;
    }

    private void updateMovementSpeedFromSprinting() {
        final AttributeInstance attribute = this.attributes.get(Attribute.MOVEMENT.getIdentifier());
        if (attribute == null) {
            return;
        }

        attribute.setValue(this.deriveMovementSpeed(this.getFlagTracker().has(EntityFlag.SPRINTING)));
        this.serverUpdatedMovementSpeed = false;
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
        return this.attributes.get(Attribute.MOVEMENT.getIdentifier()).getValue();
    }

    // Others (methods)
    public final void setPos(Vec3 vec3) {
        this.setPos(vec3, true);
    }

    public final void setPos(Vec3 vec3, boolean prev) {
        if (prev) {
            this.prevPosition = this.position.clone();
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

    public int fromRawBlockId(int id) {
        return this.mappingInfo.toIntermediary().applyAsInt(id);
    }
}
