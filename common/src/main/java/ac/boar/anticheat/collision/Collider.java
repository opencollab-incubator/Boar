package ac.boar.anticheat.collision;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.PlayerData;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;

import java.util.ArrayList;
import java.util.List;

public class Collider {
    public static boolean canFallAtLeast(final BoarPlayer player, float offsetX, float offsetZ, float f) {
        Box probe = player.boundingBox.expand(-0.025F, 0, -0.025F).offset(offsetX, -f, offsetZ);
        return player.compensatedWorld.noCollision(probe);
    }

    public static boolean canStandUp(final BoarPlayer player) {
        // should we just use 1.8 here??? maybe account for scaling and whatnot later on
        final Box standing = EntityDimensions.changing(player.dimensions.width(), 1.8F).getBoxAt(player.position).contract(1.0E-4F);
        return player.compensatedWorld.noCollision(standing);
    }

    public static Vec3 maybeBackOffFromEdge(final BoarPlayer player, final Vec3 movement) {
        // yes, vanilla does use SNEAK_DOWN here - refer to SneakMovementSystem::tickSneakMovementSystem and SendPlayerInputPacketSystem::fillInputPacket
        if (!player.onGround || !player.getInputData().contains(PlayerAuthInputData.SNEAK_DOWN)) {
            return movement;
        }

        final float f = PlayerData.STEP_HEIGHT * 1.01F;
        float d = movement.x;
        float e = movement.z;

        while (d != 0.0F && canFallAtLeast(player, d, 0, f)) {
            d = towardZero(d);
        }
        while (e != 0.0F && canFallAtLeast(player, 0, e, f)) {
            e = towardZero(e);
        }
        while ((d != 0.0F || e != 0.0F) && canFallAtLeast(player, d, e, f)) {
            d = towardZero(d);
            e = towardZero(e);
        }

        return new Vec3(d, movement.y, e);
    }

    private static float towardZero(final float value) {
        return value >= 0.0F ? Math.max(value - 0.05F, 0.0F) : Math.min(value + 0.05F, 0.0F);
    }

    // The player's move: the player box, auto-step at PlayerData.STEP_HEIGHT, and the client step test (clientAcceptsAutoStep).
    public static MoveResult collideMove(final BoarPlayer player, final Vec3 requestedMovement, final DepenetrationState state,
                                         final boolean pushTowardsClosestSpaceFlag) {
        return collideBox(player, player.boundingBox, requestedMovement, PlayerData.STEP_HEIGHT, player.onGround, state, pushTowardsClosestSpaceFlag, true);
    }

    // Moves any box without a DepenetrationState: no push-out limit and no state change (unlimitedContext).
    // Auto-step runs when stepHeight is above zero. Used for the horse (HorseTicker).
    public static MoveResult collideBox(final BoarPlayer player, final Box box, final Vec3 requestedMovement, final float stepHeight, final boolean onGround) {
        return collideBox(player, box, requestedMovement, stepHeight, onGround, null, false, false);
    }

    // Moves an actor box with its own DepenetrationState, like collideMove: the push-out limit comes from that state and from nearby actors,
    // and the move updates the state. No auto-step. Used for the boat (BoatTicker).
    public static MoveResult collideActorBox(final BoarPlayer player, final Box box, final Vec3 requestedMovement, final DepenetrationState state,
                                             final boolean pushTowardsClosestSpaceFlag) {
        return collideBox(player, box, requestedMovement, 0, false, state, pushTowardsClosestSpaceFlag, false);
    }

    // state: the mover's DepenetrationState, or null for no push-out limit.
    // clientStepTest: an auto-step is also kept only when clientAcceptsAutoStep passes. Only the player's move (collideMove) uses it.
    private static MoveResult collideBox(final BoarPlayer player, final Box box, final Vec3 requestedMovement, final float stepHeight, final boolean onGround,
                                         final DepenetrationState state, final boolean pushTowardsClosestSpaceFlag, final boolean clientStepTest) {
        final CollisionContext context = state == null ? unlimitedContext() : movementContext(player, box, state);
        final Vec3 movement = MovementCollision.clampMovement(requestedMovement);
        final Box volume = MovementCollision.collectionVolume(box, movement, stepHeight);
        final List<CollisionRecord> records = player.compensatedWorld.collectMovementColliders(volume);
        final List<Box> colliders = records.stream().map(CollisionRecord::shape).toList();

        MovementResult result = collideWithAxes(box, movement, colliders, context);
        if (state != null) {
            state.completeMove(context.penetrationSum, pushTowardsClosestSpaceFlag);
        }
        final Vec3 velocity = result.velocity();
        final boolean collisionHorizontal = movement.x != velocity.x || movement.z != velocity.z;
        final boolean grounded = onGround || movement.y != velocity.y && movement.y < 0.0F;
        if (stepHeight > 0 && grounded && collisionHorizontal) {
            final MovementResult stepResult = calculateAutoStep(box, movement, colliders, context.allowed, stepHeight);
            if (player.compensatedWorld.noCollision(stepResult.boundingBox())
                    && velocity.horizontalLengthSquared() < stepResult.velocity().horizontalLengthSquared()
                    && (!clientStepTest || clientAcceptsAutoStep(player, velocity, stepResult.velocity()))) {
                result = stepResult;
            }
        }

        return new MoveResult(movement, result.velocity(), result.boundingBox(), records);
    }

    private static boolean clientAcceptsAutoStep(final BoarPlayer player, final Vec3 collisionVelocity, final Vec3 stepVelocity) {
        float collisionDistance = player.position.add(collisionVelocity).distanceTo(player.unvalidatedPosition);
        float stepDistance = player.position.add(stepVelocity).distanceTo(player.unvalidatedPosition);
        final float correctionThreshold = Boar.getConfig().alertThreshold();
        return (correctionThreshold > 0.0F && collisionDistance > correctionThreshold) || stepDistance <= collisionDistance;
    }

    private static MovementResult collideWithAxes(final Box originalBox, final Vec3 movement, final List<Box> colliders, final CollisionContext context) {
        Vec3 yVelocity = new Vec3(0, movement.y, 0);
        Vec3 xVelocity = new Vec3(movement.x, 0, 0);
        Vec3 zVelocity = new Vec3(0, 0, movement.z);
        Box boundingBox = originalBox;

        yVelocity = clipAgainstColliders(colliders, boundingBox, yVelocity, context);
        boundingBox = boundingBox.offset(yVelocity);

        xVelocity = clipAgainstColliders(colliders, boundingBox, xVelocity, context);
        boundingBox = boundingBox.offset(xVelocity);

        zVelocity = clipAgainstColliders(colliders, boundingBox, zVelocity, context);
        boundingBox = boundingBox.offset(zVelocity);

        return new MovementResult(boundingBox, yVelocity.add(xVelocity).add(zVelocity));
    }

    private static MovementResult calculateAutoStep(final Box originalBox, final Vec3 movement, final List<Box> colliders, final Vec3 allowed,
                                                    final float stepHeight) {
        final CollisionContext context = new CollisionContext(allowed);
        // yes, this is vanilla. refer to AutoStepSystem::doAutoStepSystemImpl
        List<Box> stepColliders = new ArrayList<>(colliders.size());
        for (Box collider : colliders) {
            if (collider.minY < originalBox.maxY) {
                stepColliders.add(collider);
            }
        }

        Vec3 upVelocity = new Vec3(0, stepHeight, 0);
        Vec3 xVelocity = new Vec3(movement.x, 0, 0);
        Vec3 zVelocity = new Vec3(0, 0, movement.z);
        Box boundingBox = originalBox;

        upVelocity = clipAgainstColliders(stepColliders, boundingBox, upVelocity, context);
        boundingBox = boundingBox.offset(upVelocity);

        xVelocity = clipAgainstColliders(stepColliders, boundingBox, xVelocity, context);
        boundingBox = boundingBox.offset(xVelocity);

        zVelocity = clipAgainstColliders(stepColliders, boundingBox, zVelocity, context);
        boundingBox = boundingBox.offset(zVelocity);

        Vec3 downVelocity = clipAgainstColliders(stepColliders, boundingBox, new Vec3(0, -((upVelocity.y + xVelocity.y) + zVelocity.y), 0), context);
        boundingBox = boundingBox.offset(downVelocity);

        Vec3 stepVelocity = upVelocity.add(xVelocity).add(zVelocity).add(downVelocity);
        return new MovementResult(boundingBox, stepVelocity);
    }

    private static Vec3 clipAgainstColliders(final List<Box> colliders, final Box moving, Vec3 velocity, final CollisionContext context) {
        for (int index = colliders.size() - 1; index >= 0; index--) {
            velocity = clipCollide(colliders.get(index), moving, velocity, context);
        }
        return velocity;
    }

    private static Vec3 clipCollide(final Box stationary, final Box moving, final Vec3 velocity, final CollisionContext context) {
        final ClipCollideResult result = doClipCollide(stationary, moving, velocity);
        context.penetrationSum += result.penetration;
        return result.penetration <= component(context.allowed, result.depenetratingAxis) ? result.depenetratingVelocity : result.clippedVelocity;
    }

    private static CollisionContext movementContext(final BoarPlayer player, final Box box, final DepenetrationState state) {
        final Vec3 nearby = player.compensatedWorld.nearbyDepenetrationMagnitude(box);
        return new CollisionContext(state.maxPushOut(nearby));
    }

    // For callers without a DepenetrationState: the public collideBox (HorseTicker's ground probe and horse move).
    // No push-out limit: a box inside a block is always pushed out. No depenetration state changes.
    private static CollisionContext unlimitedContext() {
        final float allowance = Float.POSITIVE_INFINITY;
        return new CollisionContext(new Vec3(allowance, allowance, allowance));
    }

    private static final class CollisionContext {
        private final Vec3 allowed;
        private float penetrationSum;
        private CollisionContext(final Vec3 allowed) {
            this.allowed = allowed.clone();
        }
    }

    // AABB::clipCollide
    private static ClipCollideResult doClipCollide(final Box stationary, final Box moving, final Vec3 velocity) {
        ClipCollideResult result = new ClipCollideResult(velocity);
        if (stationary.minX == stationary.maxX
                && stationary.minY == stationary.maxY
                && stationary.minZ == stationary.maxZ) {
            return result;
        }

        float[] stationaryMin = {stationary.minX, stationary.minY, stationary.minZ};
        float[] stationaryMax = {stationary.maxX, stationary.maxY, stationary.maxZ};
        float[] movingMin = {moving.minX, moving.minY, moving.minZ};
        float[] movingMax = {moving.maxX, moving.maxY, moving.maxZ};
        float[] axisPenetrations = new float[3];
        float[] signedAxisPenetrations = new float[3];
        float[] normalDirections = new float[3];

        int separatingAxes = 0;
        int separatingAxis = 0;
        float resultPenetration = Float.MAX_VALUE;

        for (int axis = 0; axis < 3; axis++) {
            float minPenetration = movingMax[axis] - stationaryMin[axis];
            float maxPenetration = stationaryMax[axis] - movingMin[axis];

            if (Math.abs(minPenetration) <= 1.0E-6F) {
                minPenetration = 0.0F;
            }
            if (Math.abs(maxPenetration) <= 1.0E-6F) {
                maxPenetration = 0.0F;
            }

            float positiveMinPenetration = Math.max(0.0F, minPenetration);
            float positiveMaxPenetration = Math.max(0.0F, maxPenetration);

            if (positiveMinPenetration == 0.0F) {
                axisPenetrations[axis] = 0.0F;
                signedAxisPenetrations[axis] = minPenetration;
                normalDirections[axis] = -1.0F;
                separatingAxes++;
                separatingAxis = axis;
            } else if (positiveMaxPenetration == 0.0F) {
                axisPenetrations[axis] = 0.0F;
                signedAxisPenetrations[axis] = maxPenetration;
                normalDirections[axis] = 1.0F;
                separatingAxes++;
                separatingAxis = axis;
            } else if (positiveMinPenetration < positiveMaxPenetration) {
                axisPenetrations[axis] = positiveMinPenetration;
                signedAxisPenetrations[axis] = positiveMinPenetration;
                normalDirections[axis] = -1.0F;
            } else {
                axisPenetrations[axis] = positiveMaxPenetration;
                signedAxisPenetrations[axis] = positiveMaxPenetration;
                normalDirections[axis] = 1.0F;
            }

            if (separatingAxes > 1) {
                return result;
            }
            resultPenetration = Math.min(resultPenetration, axisPenetrations[axis]);
        }

        if (separatingAxes == 0) {
            result.penetration = resultPenetration;
            int bestAxis = 0;
            for (int axis = 1; axis < 3; axis++) {
                if (axisPenetrations[axis] < axisPenetrations[bestAxis]) {
                    bestAxis = axis;
                }
            }

            float desiredVelocity = axisPenetrations[bestAxis] * normalDirections[bestAxis];
            float currentVelocity = component(velocity, bestAxis);
            float resolvedVelocity = desiredVelocity > 0.0F
                    ? Math.max(desiredVelocity, currentVelocity)
                    : Math.min(desiredVelocity, currentVelocity);
            setComponent(result.depenetratingVelocity, bestAxis, resolvedVelocity);
            result.depenetratingAxis = bestAxis;
            return result;
        }

        float sweptPenetration = signedAxisPenetrations[separatingAxis]
                - normalDirections[separatingAxis] * component(velocity, separatingAxis);
        if (sweptPenetration <= 0.0F) {
            return result;
        }

        float resolvedVelocity = signedAxisPenetrations[separatingAxis] * normalDirections[separatingAxis];
        setComponent(result.clippedVelocity, separatingAxis, resolvedVelocity);
        setComponent(result.depenetratingVelocity, separatingAxis, resolvedVelocity);
        return result;
    }

    private static float component(final Vec3 vector, final int axis) {
        return switch (axis) {
            case 0 -> vector.x;
            case 1 -> vector.y;
            default -> vector.z;
        };
    }

    private static void setComponent(final Vec3 vector, final int axis, final float value) {
        switch (axis) {
            case 0 -> vector.x = value;
            case 1 -> vector.y = value;
            default -> vector.z = value;
        }
    }

    public record MoveResult(Vec3 requestedMovement, Vec3 clippedMovement, Box finalBox, List<CollisionRecord> records) {
    }

    private record MovementResult(Box boundingBox, Vec3 velocity) {
    }

    private static final class ClipCollideResult {
        private int depenetratingAxis;
        private float penetration;
        private final Vec3 clippedVelocity;
        private final Vec3 depenetratingVelocity;

        private ClipCollideResult(final Vec3 velocity) {
            this.clippedVelocity = velocity.clone();
            this.depenetratingVelocity = velocity.clone();
        }
    }
}
