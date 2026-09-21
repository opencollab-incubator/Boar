package ac.boar.anticheat.collision;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3i;

import java.util.List;

public final class MovementCollision {
    private MovementCollision() {
    }

    public record StandingBlock(BoarBlockState state, Vector3i position) {
    }

    // MoveCollisionSystem::fetchCollisionShapes
    public static Vec3 clampMovement(final Vec3 movement) {
        final float squared = movement.lengthSquared();
        if (squared > 256.0F) {
            final float length = (float) Math.sqrt(squared);
            return movement.divide(length).multiply(16.0F);
        }
        return movement;
    }

    // MoveCollisionSystem::fetchCollisionShapes
    public static Box collectionVolume(final Box actor, final Vec3 movement, final float stepHeight) {
        final Box swept = actor.stretch(movement);
        final Vec3 step = new Vec3(0, stepHeight, 0); // AutoStepSystem::getMaxCollisionVolume
        final Box autoStep = swept.union(actor.stretch(step)).union(actor.stretch(movement.add(step)));

        // SneakMovementSystem::getMaxCollisionVolume
        float minX = actor.minX + 0.025F, maxX = actor.maxX - 0.025F;
        float minZ = actor.minZ + 0.025F, maxZ = actor.maxZ - 0.025F;
        if (minX > maxX) {
            minX = maxX = (actor.minX + actor.maxX) * 0.5F;
        }
        if (minZ > maxZ) {
            minZ = maxZ = (actor.minZ + actor.maxZ) * 0.5F;
        }
        final float down = stepHeight * 1.01F;
        final Box lowered = new Box(minX, actor.minY - down, minZ, maxX, actor.maxY - down, maxZ);
        final Box sneak = lowered.offset(movement.x, 0, 0)
                .union(lowered.offset(0, 0, movement.z))
                .union(lowered.offset(movement.x, 0, movement.z));
        // MoveCollisionSystem::fetchCollisionShapes
        final Box margin = new Box(actor.minX, actor.minY + (-0.2F - Math.abs(movement.y)), actor.minZ, actor.maxX, actor.maxY + 0.08F, actor.maxZ);
        return swept.union(sneak).union(autoStep).union(margin);
    }

    // CollisionShapes::getBlockPosCurrentlyStandingOn
    public static StandingBlock selectStandingBlock(final Box actor, final List<CollisionRecord> records) {
        final float planeY = actor.minY - 0.2F;
        final float centerX = actor.minX + (actor.maxX - actor.minX) * 0.5F;
        final float centerZ = actor.minZ + (actor.maxZ - actor.minZ) * 0.5F;
        CollisionRecord selected = null;
        float bestVertical = Float.MAX_VALUE;
        float bestDistance = Float.MAX_VALUE;
        for (CollisionRecord record : records) {
            final Box shape = record.shape();
            final float shapeY = shape.minY + (shape.maxY - shape.minY) * 0.5F;
            final float vertical = planeY - shapeY;
            if (vertical < 0.0F) {
                continue;
            }
            final float dx = shape.minX + (shape.maxX - shape.minX) * 0.5F - centerX;
            final float dy = shapeY - planeY;
            final float dz = shape.minZ + (shape.maxZ - shape.minZ) * 0.5F - centerZ;
            final float distance = dx * dx + dy * dy + dz * dz;
            if (vertical < bestVertical || selected != null && vertical == bestVertical && distance < bestDistance) {
                selected = record;
                bestVertical = vertical;
                bestDistance = distance;
            }
        }
        if (selected == null) {
            return null;
        }
        final Box shape = selected.shape();
        if (shape.minX >= shape.maxX || shape.minY >= shape.maxY || shape.minZ >= shape.maxZ || selected.blockState() == null) {
            return null;
        }
        return new StandingBlock(selected.blockState(), Vector3i.from(GenericMath.floor(shape.minX), GenericMath.floor(shape.minY), GenericMath.floor(shape.minZ)));
    }
}
