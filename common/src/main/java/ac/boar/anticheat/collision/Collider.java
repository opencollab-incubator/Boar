package ac.boar.anticheat.collision;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.PlayerData;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Axis;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.List;

public class Collider {
    public static boolean canFallAtLeast(final BoarPlayer player, float offsetX, float offsetZ, float f) {
        Box lv = player.boundingBox.expand(-0.025F, 0, -0.025F);
        return player.compensatedWorld.noCollision(new Box(lv.minX + offsetX, lv.minY - f, lv.minZ + offsetZ, lv.maxX + offsetX, lv.minY, lv.maxZ + offsetZ));
    }

    private static boolean isAboveGround(final BoarPlayer player) {
        return player.onGround || player.fallDistance < 0.6F && !canFallAtLeast(player, 0, 0, 0.6F - player.fallDistance);
    }

    public static Vec3 maybeBackOffFromEdge(final BoarPlayer player, final Vec3 movement) {
        final float f = PlayerData.STEP_HEIGHT * 1.01F;
        if (movement.y <= 0.0 && player.getFlagTracker().has(EntityFlag.SNEAKING) && isAboveGround(player)) {
            float d = movement.x;
            float e = movement.z;
            float h = MathUtil.sign(d) * 0.05F;
            float i = MathUtil.sign(e) * 0.05F;

            while (d != 0 && canFallAtLeast(player, d, 0, f)) {
                if (Math.abs(d) <= 0.05) {
                    d = 0;
                    break;
                }

                d -= h;
            }

            while (e != 0.0 && canFallAtLeast(player, 0, e, f)) {
                if (Math.abs(e) <= 0.05) {
                    e = 0;
                    break;
                }

                e -= i;
            }

            while (d != 0.0 && e != 0.0 && canFallAtLeast(player, d, e, f)) {
                if (Math.abs(d) <= 0.05) {
                    d = 0;
                } else {
                    d -= h;
                }

                if (Math.abs(e) <= 0.05) {
                    e = 0;
                } else {
                    e -= i;
                }
            }

            return new Vec3(d, movement.y, e);
        } else {
            return movement;
        }
    }

    public static Vec3 collide(final BoarPlayer player, Vec3 movement) {
        Box box = player.boundingBox.clone();
        List<Box> collisions = player.compensatedWorld.getEntityCollisions(box.stretch(movement));
        Vec3 lv2 = movement.lengthSquared() == 0.0 ? movement : collideBoundingBox(player, movement, box, collisions);
        boolean collisionX = movement.x != lv2.x, collisionZ = movement.z != lv2.z;
        boolean verticalCollision = movement.y != lv2.y;
        boolean onGround = verticalCollision && movement.y < 0.0;
        if ((onGround || player.onGround) && (collisionX || collisionZ)) {
            Vec3 vec32 = collideBoundingBox(player, new Vec3(movement.x, PlayerData.STEP_HEIGHT, movement.z), box, collisions);
            Vec3 vec33 = collideBoundingBox(player, new Vec3(0, PlayerData.STEP_HEIGHT, 0), box.stretch(movement.x, 0, movement.z), collisions);
            if (vec33.y < PlayerData.STEP_HEIGHT) {
                Vec3 vec34 = collideBoundingBox(player, new Vec3(movement.x, 0, movement.z), box.offset(vec33), collisions).add(vec33);
                if (vec34.horizontalLengthSquared() > vec32.horizontalLengthSquared()) {
                    vec32 = vec34;
                }
            }

            if (vec32.horizontalLengthSquared() > lv2.horizontalLengthSquared()) {
                lv2 = vec32.add(collideBoundingBox(player, new Vec3(0, -vec32.y, 0), box.offset(vec32), collisions));
            }
        }

        return lv2;
    }

    private static Vec3 collideBoundingBox(final BoarPlayer player, final Vec3 movement, final Box box, final List<Box> collisions) {
        collisions.addAll(player.compensatedWorld.collectColliders(collisions, box.stretch(movement)));
        return collideWithShapes(movement, box, collisions);
    }

    private static Vec3 collideWithShapes(final Vec3 movement, Box box, final List<Box> collisions) {
        if (!collisions.isEmpty()) {
            float x = movement.x;
            float y = movement.y;
            float z = movement.z;
            if (y != 0.0) {
                y = calculateMaxOffset(Axis.Y, box, collisions, y);
                if (y != 0.0) {
                    box = box.offset(0, y, 0);
                }
            }

            if (x != 0.0) {
                x = calculateMaxOffset(Axis.X, box, collisions, x);
                if (x != 0.0) {
                    box = box.offset(x, 0, 0);
                }
            }

            if (z != 0.0) {
                z = calculateMaxOffset(Axis.Z, box, collisions, z);
            }

            return new Vec3(x, y, z);
        }

        return movement;
    }

    private static float calculateMaxOffset(final Axis axis, final Box boundingBox, final List<Box> collision, float maxDist) {
        Box box = boundingBox.clone();

        for (Box bb : collision) {
            if (Math.abs(maxDist) < Box.EPSILON) {
                return 0;
            }

            maxDist = bb.calculateMaxDistance(axis, box, maxDist);
        }

        return maxDist;
    }
}
