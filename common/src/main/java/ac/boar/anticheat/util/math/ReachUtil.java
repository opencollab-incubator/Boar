package ac.boar.anticheat.util.math;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.compensated.cache.entity.state.CachedEntityState;
import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.Pair;
import ac.boar.mappings.block.Blocks;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.jetbrains.annotations.Nullable;

public class ReachUtil {
    private static final int STEPS = 10;
    private static final float STEPS_INC = 1f / STEPS;

    private static final float REACH_RAY_LENGTH = 7F;
    private static final float BOX_EXPANSION = 0.1F;

    private static final int MAX_OBSTRUCTION_STEPS = 32;

    public record Result(float distance, @Nullable Vector3i obstruction) {
        public static final Result MISS = new Result(Float.MAX_VALUE, null);
    }

    public static Result calculateReach(
            final BoarPlayer player,
            final Pair<Vec3, Vec3> attackPositions,
            final EntityCache entity,
            final Pair<Vec3, Vec3> prevTickEntityPositions,
            final float tolerance
    ) {
        final CachedEntityState entityState = entity.getCurrent();
        final float toleranceSq = tolerance * tolerance;
        float distanceSq = Float.MAX_VALUE;
        float obstructedSq = Float.MAX_VALUE;
        Vector3i obstruction = null;

        // If the player is on touch controls and not using a crosshair, we don't have to interpolate the rotations when calculating reach.
        final boolean isLikelyTouchSnap = player.inputMode == InputMode.TOUCH &&
                (player.interactionModel == InputInteractionModel.TOUCH || (player.interactionModel == InputInteractionModel.CLASSIC && player.prevInteractRotUnchanged));

        for (float f = 0; f <= 1f; f += STEPS_INC) {
            final Vec3 rotationVec = getRotationVector(player, isLikelyTouchSnap ? 1.0f : f);
            final Vec3 reachStart = getEyePosition(player, attackPositions, f);
            final Vec3 reachEnd = reachStart.add(rotationVec.multiply(REACH_RAY_LENGTH));

            //final Vec3 primaryEntityPos = lerp(f, prevTickEntityPositions.a(), prevTickEntityPositions.b());
            final Vec3 primaryEntityPos = prevTickEntityPositions.b();
            final Box primaryBox = entityState.calculateBoundingBox(primaryEntityPos);
            final Vec3 primaryHit = calculateHitResult(primaryBox, reachStart, reachEnd);
            if (primaryHit != null) {
                final float hitDistanceSq = primaryHit.squaredDistanceTo(reachStart);
                if (hitDistanceSq <= toleranceSq) {
                    // the eye is inside the target box, so no blocks should be in between
                    final Vector3i blocker = hitDistanceSq > 0 ? findObstruction(player, reachStart, primaryHit) : null;
                    if (blocker == null) {
                        return new Result((float) Math.sqrt(hitDistanceSq), null);
                    }
                    if (hitDistanceSq < obstructedSq) {
                        obstructedSq = hitDistanceSq;
                        obstruction = blocker;
                    }
                }
                distanceSq = Math.min(distanceSq, hitDistanceSq);
            }

            //final Box altBox = entityState.getBoundingBox(f);
            final Box altBox = entityState.getBoundingBox();
            final Vec3 altHit = calculateHitResult(altBox, reachStart, reachEnd);
            if (altHit != null) {
                final float hitDistanceSq = altHit.squaredDistanceTo(reachStart);
                if (hitDistanceSq <= toleranceSq) {
                    final Vector3i blocker = hitDistanceSq > 0 ? findObstruction(player, reachStart, altHit) : null;
                    if (blocker == null) {
                        return new Result((float) Math.sqrt(hitDistanceSq), null);
                    }
                    if (hitDistanceSq < obstructedSq) {
                        obstructedSq = hitDistanceSq;
                        obstruction = blocker;
                    }
                }
                distanceSq = Math.min(distanceSq, hitDistanceSq);
            }
        }

        if (obstruction != null) {
            return new Result((float) Math.sqrt(obstructedSq), obstruction);
        }
        return distanceSq == Float.MAX_VALUE ? Result.MISS : new Result((float) Math.sqrt(distanceSq), null);
    }

    @Nullable
    private static Vector3i findObstruction(final BoarPlayer player, final Vec3 start, final Vec3 end) {
        final int eyeX = GenericMath.floor(start.x);
        final int eyeY = GenericMath.floor(start.y);
        final int eyeZ = GenericMath.floor(start.z);

        final Vector3i[] found = new Vector3i[1];
        VoxelRayTrace.blocksBetween(start, end, MAX_OBSTRUCTION_STEPS, (x, y, z) -> {
            if (x == eyeX && y == eyeY && z == eyeZ) {
                return true;
            }

            final BoarBlockState state = player.compensatedWorld.getBlockState(x, y, z, 0);
            if (state.isAir() || state.is(Blocks.BARRIER) || state.is(Blocks.INVISIBLE_BEDROCK)) {
                return true;
            }

            final Vector3i pos = Vector3i.from(x, y, z);
            for (Box box : state.findCollision(player, pos, null, false)) {
                if (box.clip(start, end).isPresent()) {
                    found[0] = pos;
                    return false;
                }
            }
            return true;
        });

        return found[0];
    }

    private static Vec3 calculateHitResult(final Box box, final Vec3 min, final Vec3 max) {
        Box expanded = box.expand(BOX_EXPANSION);
        if (expanded.contains(min)) {
            return min;
        }
        return expanded.clip(min, max).orElse(null);
    }

    private static Vec3 getRotationVector(BoarPlayer player, float f) {
        return MathUtil.getRotationVector(
                MathUtil.lerp(f, player.prevInteractRotation.getX(), player.interactRotation.getX()),
                MathUtil.lerp(f, player.prevInteractRotation.getY(), player.interactRotation.getY())
        );
    }

    private static Vec3 getEyePosition(BoarPlayer player, Pair<Vec3, Vec3> pair, float f) {
        float lerpX = MathUtil.lerp(f, pair.a().x, pair.b().x);
        float lerpY = MathUtil.lerp(f, pair.a().y, pair.b().y) + player.dimensions.eyeHeight();
        float lerpZ = MathUtil.lerp(f, pair.a().z, pair.b().z);
        return new Vec3(lerpX, lerpY, lerpZ);
    }
}
