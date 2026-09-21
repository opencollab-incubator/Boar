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

    public static Vec3 collide(final BoarPlayer player, Vec3 movement) {
        return collide(player, movement, player.stuckInCollider, null);
    }

    public static Vec3 collide(final BoarPlayer player, final Vec3 movement, final boolean oneWay, final Vec3 penetration) {
        return collideMove(player, movement, oneWay, penetration).clippedMovement();
    }

    public static MoveResult collideMove(final BoarPlayer player, final Vec3 requestedMovement, final boolean oneWay, final Vec3 penetration) {
        final Vec3 movement = MovementCollision.clampMovement(requestedMovement);
        final Box boundingBox = player.boundingBox.clone();
        final Box volume = MovementCollision.collectionVolume(boundingBox, movement, PlayerData.STEP_HEIGHT);
        final List<CollisionRecord> records = player.compensatedWorld.collectMovementColliders(volume, boundingBox.stretch(requestedMovement));
        final List<Box> colliders = records.stream().map(CollisionRecord::shape).toList();

        MovementResult collisionResult = collideWithAxes(boundingBox, movement, colliders, oneWay, penetration);
        Vec3 collisionVelocity = collisionResult.velocity();

        boolean collisionX = movement.x != collisionVelocity.x;
        boolean collisionY = movement.y != collisionVelocity.y;
        boolean collisionZ = movement.z != collisionVelocity.z;
        boolean onGround = player.onGround || collisionY && movement.y < 0.0F;

        if (onGround && (collisionX || collisionZ)) {
            MovementResult stepResult = calculateAutoStep(boundingBox, movement, colliders, oneWay);
            if (player.compensatedWorld.noCollision(stepResult.boundingBox())
                    && collisionVelocity.horizontalLengthSquared() < stepResult.velocity().horizontalLengthSquared()
                    && clientAcceptsAutoStep(player, collisionVelocity, stepResult.velocity())) {
                collisionVelocity = stepResult.velocity();
                collisionResult = stepResult;
            }
        }

        return new MoveResult(movement, collisionVelocity, collisionResult.boundingBox(), records);
    }

    private static boolean clientAcceptsAutoStep(final BoarPlayer player, final Vec3 collisionVelocity, final Vec3 stepVelocity) {
        float collisionDistance = player.position.add(collisionVelocity).distanceTo(player.unvalidatedPosition);
        float stepDistance = player.position.add(stepVelocity).distanceTo(player.unvalidatedPosition);
        final float correctionThreshold = Boar.getConfig().alertThreshold();
        return (correctionThreshold > 0.0F && collisionDistance > correctionThreshold) || stepDistance <= collisionDistance;
    }

    private static MovementResult collideWithAxes(final Box originalBox, final Vec3 movement, final List<Box> colliders,
                                                  final boolean oneWay, final Vec3 penetration) {
        Vec3 yVelocity = new Vec3(0, movement.y, 0);
        Vec3 xVelocity = new Vec3(movement.x, 0, 0);
        Vec3 zVelocity = new Vec3(0, 0, movement.z);
        Box boundingBox = originalBox;

        yVelocity = clipAgainstColliders(colliders, boundingBox, yVelocity, oneWay, penetration);
        boundingBox = boundingBox.offset(yVelocity);

        xVelocity = clipAgainstColliders(colliders, boundingBox, xVelocity, oneWay, penetration);
        boundingBox = boundingBox.offset(xVelocity);

        zVelocity = clipAgainstColliders(colliders, boundingBox, zVelocity, oneWay, penetration);
        boundingBox = boundingBox.offset(zVelocity);

        return new MovementResult(boundingBox, yVelocity.add(xVelocity).add(zVelocity));
    }

    private static MovementResult calculateAutoStep(final Box originalBox, final Vec3 movement, final List<Box> colliders, final boolean oneWay) {
        // yes, this is vanilla. refer to AutoStepSystem::doAutoStepSystemImpl
        List<Box> stepColliders = new ArrayList<>(colliders.size());
        for (Box collider : colliders) {
            if (collider.minY < originalBox.maxY) {
                stepColliders.add(collider);
            }
        }

        Vec3 upVelocity = new Vec3(0, PlayerData.STEP_HEIGHT, 0);
        Vec3 xVelocity = new Vec3(movement.x, 0, 0);
        Vec3 zVelocity = new Vec3(0, 0, movement.z);
        Box boundingBox = originalBox;

        upVelocity = clipAgainstColliders(stepColliders, boundingBox, upVelocity, oneWay, null);
        boundingBox = boundingBox.offset(upVelocity);

        xVelocity = clipAgainstColliders(stepColliders, boundingBox, xVelocity, oneWay, null);
        boundingBox = boundingBox.offset(xVelocity);

        zVelocity = clipAgainstColliders(stepColliders, boundingBox, zVelocity, oneWay, null);
        boundingBox = boundingBox.offset(zVelocity);

        Vec3 downVelocity = clipAgainstColliders(stepColliders, boundingBox, upVelocity.multiply(-1.0F), oneWay, null);
        boundingBox = boundingBox.offset(downVelocity);

        Vec3 stepVelocity = upVelocity.add(xVelocity).add(zVelocity).add(downVelocity);
        return new MovementResult(boundingBox, stepVelocity);
    }

    private static Vec3 clipAgainstColliders(final List<Box> colliders, final Box moving, Vec3 velocity,
                                             final boolean oneWay, final Vec3 penetration) {
        for (int index = colliders.size() - 1; index >= 0; index--) {
            velocity = clipCollide(colliders.get(index), moving, velocity, oneWay, penetration);
        }
        return velocity;
    }

    static Vec3 clipCollide(final Box stationary, final Box moving, final Vec3 velocity,
                            final boolean oneWay, final Vec3 penetration) {
        ClipCollideResult result = doClipCollide(stationary, moving, velocity);
        if (penetration != null && component(penetration, result.depenetratingAxis) < result.penetration) {
            setComponent(penetration, result.depenetratingAxis, result.penetration);
        }
        return oneWay ? result.clippedVelocity : result.depenetratingVelocity;
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
