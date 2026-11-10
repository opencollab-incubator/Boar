package ac.boar.anticheat.util.reach;

import ac.boar.anticheat.compensated.cache.entity.state.CachedEntityState;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.mappings.entity.EntityTypes;
import lombok.Getter;

public class PositionInterpolator implements Cloneable {
    private final CachedEntityState entity;
    private final int lerpDuration;

    @Getter
    private Vec3 targetPos;
    @Getter
    private int step;

    public PositionInterpolator(CachedEntityState entity) {
        this.entity = entity;
        this.lerpDuration = 3;
    }

    // lerpDuration is always 3 (constructor), so a new target always gets steps.
    public void refreshPositionAndAngles(Vec3 pow) {
        this.targetPos = pow;
        this.step = lerpDuration;
    }

    public boolean isInterpolating() {
        return this.step > 0;
    }

    public void tick() {
        if (!this.isInterpolating()) {
            this.clear();
            return;
        }

        // step > 0 here, so targetPos is set: only refreshPositionAndAngles (with a non-null position) makes step positive, and clear() resets both.
        this.entity.setPos(step(this.entity.getPos(), this.targetPos, this.step));

        --this.step;
    }

    // One interpolation step from `from` toward `target`, with `steps` steps left (steps > 0). The last step lands on the target.
    // The native client multiplies by a float reciprocal (1 / steps), then adds. It does not divide delta by steps.
    // Also used for the teleport interpolation (TeleportHandler.processTeleport).
    public static Vec3 step(final Vec3 from, final Vec3 target, final int steps) {
        if (steps == 1) {
            return target.clone();
        }
        final float reciprocal = 1.0F / steps;
        final float x = from.getX() + (target.getX() - from.getX()) * reciprocal;
        final float y = from.getY() + (target.getY() - from.getY()) * reciprocal;
        final float z = from.getZ() + (target.getZ() - from.getZ()) * reciprocal;
        return new Vec3(x, y, z);
    }

    public void clear() {
        this.step = 0;
        this.targetPos = null;
    }

    @Override
    public PositionInterpolator clone() {
        final PositionInterpolator clone = new PositionInterpolator(this.entity);
        clone.targetPos = this.targetPos;
        clone.step = this.step;
        return clone;
    }
}

