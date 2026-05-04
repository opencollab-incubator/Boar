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
        if (entity.getEntity() == null) {
            this.lerpDuration = 3;
        } else {
            this.lerpDuration = entity.getEntity().getType().is(EntityTypes.PLAYER) ? 3 : 6;
        }
    }

    public void refreshPositionAndAngles(Vec3 pow) {
        if (this.lerpDuration == 0) {
            this.entity.setPos(pow);
            this.clear();
            return;
        }

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

        if (this.step > 0 && this.targetPos != null) {
            float x = this.entity.getPos().getX() + (this.targetPos.getX() - this.entity.getPos().getX()) / this.step;
            float y = this.entity.getPos().getY() + (this.targetPos.getY() - this.entity.getPos().getY()) / this.step;
            float z = this.entity.getPos().getZ() + (this.targetPos.getZ() - this.entity.getPos().getZ()) / this.step;
            this.entity.setPos(new Vec3(x, y, z));

            --this.step;
        }
    }

    private void clear() {
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

