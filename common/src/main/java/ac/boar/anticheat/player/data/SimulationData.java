package ac.boar.anticheat.player.data;

import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.input.PredictionResult;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public final class SimulationData {

    private Vec3 position, prevPosition;
    private Box boundingBox;
    private Vec3 velocity, lastTickFinalVelocity;
    private Vector bestPossibility;
    private boolean velocityAliasedToBestPossibility;

    private boolean onGround, horizontalCollision, verticalCollision;
    private boolean stuckInCollider, penetratedLastFrame;
    private Vec3 stuckSpeedMultiplier;
    private float fallDistance;
    private boolean bounce;
    private float minBounceYVel;

    private PredictionResult predictionResult;
    private Vec3 beforeCollision, afterCollision;

    private boolean touchingWater;
    private Map<Fluid, Float> fluidHeight;
    private boolean beingPushByLava, soulSandBelow, nearBamboo, hasDepthStrider;
    private boolean scaffoldDescend;

    private int ticksSinceCanSlowdown;
    private int ticksSinceSwimming, ticksSinceCrawling;
    private int glideBoostTicks;

    private boolean dirtyRiptide, dirtySpinStop, thisTickSpinAttack, thisTickOnGroundSpinAttack;
    private int autoSpinAttackTicks;
    private ItemData riptideItem;

    private Set<PlayerAuthInputData> inputData;
    private Set<Ability> abilities;
    private EnumSet<EntityFlag> entityFlags;
    private boolean flying, wasFlying;
    private Map<String, AttributeInstance> attributes;

    private Vec3 input;

    private boolean serverSprinting, serverSprintingApplied, serverUpdatedMovementSpeed;
    private boolean serverSneaking, serverSneakingApplied;
    private Float pendingServerMovementSpeed, airSpeedOverride;
    private PlayerData.SprintContext sprintContext;
    private PlayerData.SneakContext sneakContext;

    private SimulationData() {
    }

    public static SimulationData from(final PlayerData player) {
        final SimulationData data = new SimulationData();

        data.position = player.position.clone();
        data.prevPosition = player.prevPosition.clone();
        data.boundingBox = player.boundingBox.clone();
        data.velocity = player.velocity.clone();
        data.lastTickFinalVelocity = player.lastTickFinalVelocity.clone();
        data.bestPossibility = copyOf(player.bestPossibility);
        data.velocityAliasedToBestPossibility = player.velocity == player.bestPossibility.getVelocity();

        data.onGround = player.onGround;
        data.horizontalCollision = player.horizontalCollision;
        data.verticalCollision = player.verticalCollision;
        data.stuckInCollider = player.stuckInCollider;
        data.penetratedLastFrame = player.penetratedLastFrame;
        data.stuckSpeedMultiplier = player.stuckSpeedMultiplier.clone();
        data.fallDistance = player.fallDistance;
        data.bounce = player.bounce;
        data.minBounceYVel = player.minBounceYVel;

        data.predictionResult = copyOf(player.predictionResult);
        data.beforeCollision = player.beforeCollision.clone();
        data.afterCollision = player.afterCollision.clone();

        data.touchingWater = player.touchingWater;
        data.fluidHeight = new HashMap<>(player.fluidHeight);
        data.beingPushByLava = player.beingPushByLava;
        data.soulSandBelow = player.soulSandBelow;
        data.nearBamboo = player.nearBamboo;
        data.hasDepthStrider = player.hasDepthStrider;
        data.scaffoldDescend = player.scaffoldDescend;

        data.ticksSinceCanSlowdown = player.ticksSinceCanSlowdown;
        data.ticksSinceSwimming = player.ticksSinceSwimming;
        data.ticksSinceCrawling = player.ticksSinceCrawling;
        data.glideBoostTicks = player.glideBoostTicks;

        data.dirtyRiptide = player.dirtyRiptide;
        data.dirtySpinStop = player.dirtySpinStop;
        data.thisTickSpinAttack = player.thisTickSpinAttack;
        data.thisTickOnGroundSpinAttack = player.thisTickOnGroundSpinAttack;
        data.autoSpinAttackTicks = player.autoSpinAttackTicks;
        data.riptideItem = player.riptideItem;

        data.inputData = new HashSet<>(player.getInputData());
        data.abilities = new HashSet<>(player.abilities);
        data.entityFlags = player.getFlagTracker().cloneFlags();
        data.flying = player.getFlagTracker().isFlying();
        data.wasFlying = player.getFlagTracker().isWasFlying();

        data.attributes = copyAttributes(player.attributes);

        data.input = player.input.clone();

        data.serverSprinting = player.serverSprinting;
        data.serverSprintingApplied = player.serverSprintingApplied;
        data.serverSneaking = player.serverSneaking;
        data.serverSneakingApplied = player.serverSneakingApplied;
        data.serverUpdatedMovementSpeed = player.serverUpdatedMovementSpeed;
        data.pendingServerMovementSpeed = player.pendingServerMovementSpeed;
        data.airSpeedOverride = player.airSpeedOverride;
        data.sprintContext = player.sprintContext;
        data.sneakContext = player.sneakContext;

        return data;
    }

    public void apply(final PlayerData player) {
        player.position = this.position.clone();
        player.prevPosition = this.prevPosition.clone();
        player.boundingBox = this.boundingBox.clone();

        player.bestPossibility = copyOf(this.bestPossibility);
        player.velocity = this.velocityAliasedToBestPossibility ? player.bestPossibility.getVelocity() : this.velocity.clone();
        player.lastTickFinalVelocity = this.lastTickFinalVelocity.clone();

        player.onGround = this.onGround;
        player.horizontalCollision = this.horizontalCollision;
        player.verticalCollision = this.verticalCollision;
        player.stuckInCollider = this.stuckInCollider;
        player.penetratedLastFrame = this.penetratedLastFrame;
        player.stuckSpeedMultiplier = this.stuckSpeedMultiplier.clone();
        player.fallDistance = this.fallDistance;
        player.bounce = this.bounce;
        player.minBounceYVel = this.minBounceYVel;

        player.predictionResult = copyOf(this.predictionResult);
        player.beforeCollision = this.beforeCollision.clone();
        player.afterCollision = this.afterCollision.clone();

        player.touchingWater = this.touchingWater;
        player.fluidHeight.clear();
        player.fluidHeight.putAll(this.fluidHeight);
        player.beingPushByLava = this.beingPushByLava;
        player.soulSandBelow = this.soulSandBelow;
        player.nearBamboo = this.nearBamboo;
        player.hasDepthStrider = this.hasDepthStrider;
        player.scaffoldDescend = this.scaffoldDescend;

        player.ticksSinceCanSlowdown = this.ticksSinceCanSlowdown;
        player.ticksSinceSwimming = this.ticksSinceSwimming;
        player.ticksSinceCrawling = this.ticksSinceCrawling;
        player.glideBoostTicks = this.glideBoostTicks;

        player.dirtyRiptide = this.dirtyRiptide;
        player.dirtySpinStop = this.dirtySpinStop;
        player.thisTickSpinAttack = this.thisTickSpinAttack;
        player.thisTickOnGroundSpinAttack = this.thisTickOnGroundSpinAttack;
        player.autoSpinAttackTicks = this.autoSpinAttackTicks;
        player.riptideItem = this.riptideItem;

        player.getInputData().clear();
        player.getInputData().addAll(this.inputData);
        player.abilities.clear();
        player.abilities.addAll(this.abilities);
        player.getFlagTracker().restore(this.entityFlags, this.flying, this.wasFlying);

        copyAttributesInto(this.attributes, player.attributes);

        player.input = this.input.clone();

        player.serverSprinting = this.serverSprinting;
        player.serverSprintingApplied = this.serverSprintingApplied;
        player.serverSneaking = this.serverSneaking;
        player.serverSneakingApplied = this.serverSneakingApplied;
        player.serverUpdatedMovementSpeed = this.serverUpdatedMovementSpeed;
        player.pendingServerMovementSpeed = this.pendingServerMovementSpeed;
        player.airSpeedOverride = this.airSpeedOverride;
        player.sprintContext = this.sprintContext;
        player.sneakContext = this.sneakContext;

        player.inBlockState = null;
        player.cachedOnPos = null;
    }

    public SimulationData copy() {
        final SimulationData data = new SimulationData();

        data.position = this.position.clone();
        data.prevPosition = this.prevPosition.clone();
        data.boundingBox = this.boundingBox.clone();
        data.velocity = this.velocity.clone();
        data.lastTickFinalVelocity = this.lastTickFinalVelocity.clone();
        data.bestPossibility = copyOf(this.bestPossibility);
        data.velocityAliasedToBestPossibility = this.velocityAliasedToBestPossibility;

        data.onGround = this.onGround;
        data.horizontalCollision = this.horizontalCollision;
        data.verticalCollision = this.verticalCollision;
        data.stuckInCollider = this.stuckInCollider;
        data.penetratedLastFrame = this.penetratedLastFrame;
        data.stuckSpeedMultiplier = this.stuckSpeedMultiplier.clone();
        data.fallDistance = this.fallDistance;
        data.bounce = this.bounce;
        data.minBounceYVel = this.minBounceYVel;

        data.predictionResult = copyOf(this.predictionResult);
        data.beforeCollision = this.beforeCollision.clone();
        data.afterCollision = this.afterCollision.clone();

        data.touchingWater = this.touchingWater;
        data.fluidHeight = new HashMap<>(this.fluidHeight);
        data.beingPushByLava = this.beingPushByLava;
        data.soulSandBelow = this.soulSandBelow;
        data.nearBamboo = this.nearBamboo;
        data.hasDepthStrider = this.hasDepthStrider;
        data.scaffoldDescend = this.scaffoldDescend;

        data.ticksSinceCanSlowdown = this.ticksSinceCanSlowdown;
        data.ticksSinceSwimming = this.ticksSinceSwimming;
        data.ticksSinceCrawling = this.ticksSinceCrawling;
        data.glideBoostTicks = this.glideBoostTicks;

        data.dirtyRiptide = this.dirtyRiptide;
        data.dirtySpinStop = this.dirtySpinStop;
        data.thisTickSpinAttack = this.thisTickSpinAttack;
        data.thisTickOnGroundSpinAttack = this.thisTickOnGroundSpinAttack;
        data.autoSpinAttackTicks = this.autoSpinAttackTicks;
        data.riptideItem = this.riptideItem;

        data.inputData = new HashSet<>(this.inputData);
        data.abilities = new HashSet<>(this.abilities);
        data.entityFlags = EnumSet.copyOf(this.entityFlags);
        data.flying = this.flying;
        data.wasFlying = this.wasFlying;

        data.attributes = copyAttributes(this.attributes);

        data.input = this.input.clone();

        data.serverSprinting = this.serverSprinting;
        data.serverSprintingApplied = this.serverSprintingApplied;
        data.serverSneaking = this.serverSneaking;
        data.serverSneakingApplied = this.serverSneakingApplied;
        data.serverUpdatedMovementSpeed = this.serverUpdatedMovementSpeed;
        data.pendingServerMovementSpeed = this.pendingServerMovementSpeed;
        data.airSpeedOverride = this.airSpeedOverride;
        data.sprintContext = this.sprintContext;
        data.sneakContext = this.sneakContext;

        return data;
    }

    private static Map<String, AttributeInstance> copyAttributes(final Map<String, AttributeInstance> source) {
        final Map<String, AttributeInstance> copy = HashMap.newHashMap(source.size());
        copyAttributesInto(source, copy);
        return copy;
    }

    private static void copyAttributesInto(final Map<String, AttributeInstance> source, final Map<String, AttributeInstance> target) {
        target.clear();
        for (final Map.Entry<String, AttributeInstance> entry : source.entrySet()) {
            target.put(entry.getKey(), entry.getValue().copy());
        }
    }

    private static Vector copyOf(final Vector vector) {
        return new Vector(vector.getType(), vector.getVelocity().clone());
    }

    private static PredictionResult copyOf(final PredictionResult result) {
        return new PredictionResult(result.before().clone(), result.after().clone(), result.tickEnd().clone());
    }
}
