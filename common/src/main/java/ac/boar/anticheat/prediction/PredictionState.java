package ac.boar.anticheat.prediction;

import ac.boar.anticheat.data.Fluid;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.input.PredictionData;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.prediction.engine.data.Vector;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public final class PredictionState {

    private final Vec3 position, prevPosition;
    private final Box boundingBox;
    private final float nativeOriginY;

    private final Vec3 velocity, lastTickFinalVelocity, input;
    private final Vector certainVelocity, bestPossibility;
    private final PredictionData predictionResult;
    private final Vec3 beforeCollision, afterCollision;

    private final boolean onGround, horizontalCollision, verticalCollision;
    private final boolean stuckInCollider, penetratedLastFrame;
    private final Vec3 stuckSpeedMultiplier;

    private final boolean touchingWater, headInWater;
    private final Fluid selectedFluid;
    private final Map<Fluid, Float> fluidHeight;

    private final BoarBlockState inBlockState;
    private final Vector3i cachedOnPos;
    private final boolean scaffoldDescend;

    private final int ticksSinceCanSlowdown;
    private final boolean prevUsingItemFlag;
    private final long lastItemUseStateChangeTick;

    private final boolean dirtyRiptide, dirtySpinStop, thisTickSpinAttack;
    private final int autoSpinAttackTicks;
    private final ItemData riptideItem;

    private final EnumSet<EntityFlag> flags;

    private PredictionState(final BoarPlayer player) {
        this.position = player.position.clone();
        this.prevPosition = player.prevPosition.clone();
        this.boundingBox = player.boundingBox.clone();
        this.nativeOriginY = player.nativeOriginY;

        this.velocity = player.velocity.clone();
        this.lastTickFinalVelocity = player.lastTickFinalVelocity.clone();
        this.input = player.input.clone();
        this.certainVelocity = player.certainVelocity;
        this.bestPossibility = player.bestPossibility;
        this.predictionResult = player.predictionResult;
        this.beforeCollision = player.beforeCollision.clone();
        this.afterCollision = player.afterCollision.clone();

        this.onGround = player.onGround;
        this.horizontalCollision = player.horizontalCollision;
        this.verticalCollision = player.verticalCollision;
        this.stuckInCollider = player.stuckInCollider;
        this.penetratedLastFrame = player.penetratedLastFrame;
        this.stuckSpeedMultiplier = player.stuckSpeedMultiplier.clone();

        this.touchingWater = player.touchingWater;
        this.headInWater = player.headInWater;
        this.selectedFluid = player.selectedFluid;
        this.fluidHeight = new HashMap<>(player.fluidHeight);

        this.inBlockState = player.inBlockState;
        this.cachedOnPos = player.cachedOnPos;
        this.scaffoldDescend = player.scaffoldDescend;

        this.ticksSinceCanSlowdown = player.ticksSinceCanSlowdown;
        this.prevUsingItemFlag = player.prevUsingItemFlag;
        this.lastItemUseStateChangeTick = player.lastItemUseStateChangeTick;

        this.dirtyRiptide = player.dirtyRiptide;
        this.dirtySpinStop = player.dirtySpinStop;
        this.thisTickSpinAttack = player.thisTickSpinAttack;
        this.autoSpinAttackTicks = player.autoSpinAttackTicks;
        this.riptideItem = player.riptideItem;

        this.flags = player.getFlagTracker().cloneFlags();
    }

    public static PredictionState capture(final BoarPlayer player) {
        return new PredictionState(player);
    }

    public Vec3 position() {
        return this.position;
    }

    // Clones again on every restore, so the same state can be restored more than once.
    public void restore(final BoarPlayer player) {
        player.position = this.position.clone();
        player.prevPosition = this.prevPosition.clone();
        player.boundingBox = this.boundingBox.clone();
        player.nativeOriginY = this.nativeOriginY;

        player.velocity = this.velocity.clone();
        player.lastTickFinalVelocity = this.lastTickFinalVelocity.clone();
        player.input = this.input.clone();
        player.certainVelocity = this.certainVelocity;
        player.bestPossibility = this.bestPossibility;
        player.predictionResult = this.predictionResult;
        player.beforeCollision = this.beforeCollision.clone();
        player.afterCollision = this.afterCollision.clone();

        player.onGround = this.onGround;
        player.horizontalCollision = this.horizontalCollision;
        player.verticalCollision = this.verticalCollision;
        player.stuckInCollider = this.stuckInCollider;
        player.penetratedLastFrame = this.penetratedLastFrame;
        player.stuckSpeedMultiplier = this.stuckSpeedMultiplier.clone();

        player.touchingWater = this.touchingWater;
        player.headInWater = this.headInWater;
        player.selectedFluid = this.selectedFluid;
        player.fluidHeight.clear();
        player.fluidHeight.putAll(this.fluidHeight);

        player.inBlockState = this.inBlockState;
        player.cachedOnPos = this.cachedOnPos;
        player.scaffoldDescend = this.scaffoldDescend;

        player.ticksSinceCanSlowdown = this.ticksSinceCanSlowdown;
        player.prevUsingItemFlag = this.prevUsingItemFlag;
        player.lastItemUseStateChangeTick = this.lastItemUseStateChangeTick;

        player.dirtyRiptide = this.dirtyRiptide;
        player.dirtySpinStop = this.dirtySpinStop;
        player.thisTickSpinAttack = this.thisTickSpinAttack;
        player.autoSpinAttackTicks = this.autoSpinAttackTicks;
        player.riptideItem = this.riptideItem;

        player.getFlagTracker().restoreFlags(this.flags);
    }
}
