package ac.boar.anticheat.collision;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.PlayerData;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.List;

public class Collider {
    public static boolean canFallAtLeast(final BoarPlayer player, float offsetX, float offsetZ, float f) {
        Box lv = player.boundingBox.expand(-0.025F, 0, -0.025F);
        return player.compensatedWorld.noCollision(new Box(lv.minX + offsetX, lv.minY - f, lv.minZ + offsetZ, lv.maxX + offsetX, lv.minY, lv.maxZ + offsetZ));
    }

    public static Vec3 maybeBackOffFromEdge(final BoarPlayer player, final Vec3 movement) {
        final float f = PlayerData.STEP_HEIGHT * 1.01F;
        if (movement.y <= 0.0 && player.getFlagTracker().has(EntityFlag.SNEAKING) && player.onGround) {
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
        return collide(player, movement, player.stuckInCollider, null);
    }

    public static Vec3 collide(final BoarPlayer player, Vec3 movement, boolean oneWay, Vec3 penetration) {
        // NOTE: unlike Boar's old Java-style collide, we do NOT short-circuit on zero movement here.
        // misanthroper's clipCollide runs the collision passes even at zero velocity, so a box already
        // overlapping a collider still depenetrates (and updates penetration state). Skipping that would
        // defeat the stuck-in-collider handling. Non-overlapping boxes still resolve to zero movement.
        Box box = player.boundingBox.clone();
        Box stretched = box.stretch(movement);
        List<Box> bbList = player.compensatedWorld.getEntityCollisions(stretched);
        bbList.addAll(player.compensatedWorld.collectColliders(List.of(), stretched));

        Vec3 yVel = new Vec3(0, movement.y, 0);
        Vec3 xVel = new Vec3(movement.x, 0, 0);
        Vec3 zVel = new Vec3(0, 0, movement.z);

        for (int i = bbList.size() - 1; i >= 0; i--) {
            yVel = BBClipCollide(bbList.get(i), box, yVel, oneWay, penetration);
        }
        box = box.offset(yVel);

        for (int i = bbList.size() - 1; i >= 0; i--) {
            xVel = BBClipCollide(bbList.get(i), box, xVel, oneWay, penetration);
        }
        box = box.offset(xVel);

        for (int i = bbList.size() - 1; i >= 0; i--) {
            zVel = BBClipCollide(bbList.get(i), box, zVel, oneWay, penetration);
        }

        Vec3 collisionVel = yVel.add(xVel).add(zVel);
        boolean xCol = movement.x != collisionVel.x;
        boolean zCol = movement.z != collisionVel.z;
        boolean yCol = movement.y != collisionVel.y;
        boolean onGround = player.onGround || (yCol && movement.y < 0.0F);

        if (onGround && (xCol || zCol)) {
            Vec3 sY = new Vec3(0, PlayerData.STEP_HEIGHT, 0);
            Vec3 sX = new Vec3(movement.x, 0, 0);
            Vec3 sZ = new Vec3(0, 0, movement.z);
            Box sBB = player.boundingBox.clone();

            for (Box b : bbList) {
                sY = BBClipCollide(b, sBB, sY, oneWay, null);
            }
            sBB = sBB.offset(sY);

            for (Box b : bbList) {
                sX = BBClipCollide(b, sBB, sX, oneWay, null);
            }
            sBB = sBB.offset(sX);

            for (Box b : bbList) {
                sZ = BBClipCollide(b, sBB, sZ, oneWay, null);
            }
            sBB = sBB.offset(sZ);

            Vec3 invY = sY.multiply(-1.0F);
            for (Box b : bbList) {
                invY = BBClipCollide(b, sBB, invY, oneWay, null);
            }
            sBB = sBB.offset(invY);
            sY = sY.add(invY);

            Vec3 stepVel = sY.add(sX).add(sZ);
            if (player.compensatedWorld.noCollision(sBB)
                    && collisionVel.horizontalLengthSquared() < stepVel.horizontalLengthSquared()) {
                collisionVel = stepVel;
            }
        }

        return collisionVel;
    }

    private static Vec3 BBClipCollide(final Box stationary, final Box moving, final Vec3 vel, boolean oneWay, Vec3 penetration) {
        ClipCollideResult result = doBBClipCollide(stationary, moving, vel);
        if (penetration != null) {
            float cur = component(penetration, result.depenetratingAxis);
            if (cur < result.penetration) {
                setComponent(penetration, result.depenetratingAxis, result.penetration);
            }
        }

        return oneWay ? result.clippedVelocity : result.depenetratingVelocity;
    }

    private static ClipCollideResult doBBClipCollide(final Box stationary, final Box moving, final Vec3 velocity) {
        ClipCollideResult result = new ClipCollideResult(velocity);
        if (stationary.minX == stationary.maxX && stationary.minY == stationary.maxY && stationary.minZ == stationary.maxZ) {
            return result;
        }

        float[] vel = new float[]{velocity.x, velocity.y, velocity.z};
        float[] sMin = new float[]{stationary.minX, stationary.minY, stationary.minZ};
        float[] sMax = new float[]{stationary.maxX, stationary.maxY, stationary.maxZ};
        float[] mMin = new float[]{moving.minX, moving.minY, moving.minZ};
        float[] mMax = new float[]{moving.maxX, moving.maxY, moving.maxZ};

        float[] axisPenetrations = new float[3];
        float[] axisPenetrationsSigned = new float[3];
        float[] normalDirs = new float[3];
        int separatingAxes = 0;
        int separatingAxis = 0;
        float resultPenetration = Float.MAX_VALUE - 1.0F;

        for (int i = 0; i < 3; i++) {
            float minPen = mMax[i] - sMin[i];
            float maxPen = sMax[i] - mMin[i];

            if (Math.abs(minPen) <= 1.0E-7F) {
                minPen = 0.0F;
            }
            if (Math.abs(maxPen) <= 1.0E-7F) {
                maxPen = 0.0F;
            }

            float minPositive = Math.max(0.0F, minPen);
            float maxPositive = Math.max(0.0F, maxPen);

            if (minPositive == 0.0F) {
                axisPenetrations[i] = 0.0F;
                axisPenetrationsSigned[i] = minPen;
                normalDirs[i] = -1.0F;
                separatingAxes++;
                separatingAxis = i;
            } else if (maxPositive == 0.0F) {
                axisPenetrations[i] = 0.0F;
                axisPenetrationsSigned[i] = maxPen;
                normalDirs[i] = 1.0F;
                separatingAxes++;
                separatingAxis = i;
            } else if (minPositive < maxPositive) {
                axisPenetrations[i] = minPositive;
                axisPenetrationsSigned[i] = minPositive;
                normalDirs[i] = -1.0F;
            } else {
                axisPenetrations[i] = maxPositive;
                axisPenetrationsSigned[i] = maxPositive;
                normalDirs[i] = 1.0F;
            }

            if (separatingAxes > 1) {
                return result;
            }
            resultPenetration = Math.min(resultPenetration, axisPenetrations[i]);
        }

        if (separatingAxes == 0) {
            result.penetration = resultPenetration;
            int bestAxis = 0;
            for (int i = 1; i < 3; i++) {
                if (axisPenetrations[i] < axisPenetrations[bestAxis]) {
                    bestAxis = i;
                }
            }

            float desiredVelocity = axisPenetrations[bestAxis] * normalDirs[bestAxis];
            float[] depen = new float[]{vel[0], vel[1], vel[2]};
            if (desiredVelocity > 0.0F) {
                depen[bestAxis] = Math.max(desiredVelocity, vel[bestAxis]);
            } else {
                depen[bestAxis] = Math.min(desiredVelocity, vel[bestAxis]);
            }
            result.depenetratingVelocity.x = depen[0];
            result.depenetratingVelocity.y = depen[1];
            result.depenetratingVelocity.z = depen[2];
            result.depenetratingAxis = bestAxis;
            return result;
        }

        float sweptPenetration = axisPenetrationsSigned[separatingAxis] - (normalDirs[separatingAxis] * vel[separatingAxis]);
        if (sweptPenetration <= 0.0F) {
            return result;
        }

        float resolvedVelocity = axisPenetrationsSigned[separatingAxis] * normalDirs[separatingAxis];
        setComponent(result.clippedVelocity, separatingAxis, resolvedVelocity);
        setComponent(result.depenetratingVelocity, separatingAxis, resolvedVelocity);
        return result;
    }

    private static float component(final Vec3 vec3, int axis) {
        return switch (axis) {
            case 0 -> vec3.x;
            case 1 -> vec3.y;
            default -> vec3.z;
        };
    }

    private static void setComponent(final Vec3 vec3, int axis, float value) {
        switch (axis) {
            case 0 -> vec3.x = value;
            case 1 -> vec3.y = value;
            default -> vec3.z = value;
        }
    }

    private static class ClipCollideResult {
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
