package ac.boar.anticheat.compensated.cache.entity.state;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.MathUtil;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.anticheat.util.reach.PositionInterpolator;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class CachedEntityState {
    private final BoarPlayer player;
    private final EntityCache entity;
    private Vec3 prevPos = Vec3.ZERO;
    private Vec3 pos = Vec3.ZERO;
    private PositionInterpolator interpolator;

    public CachedEntityState(BoarPlayer player, EntityCache entity) {
        this.player = player;
        this.entity = entity;

        this.interpolator = new PositionInterpolator(this);
    }

    public void tick() {
        if (this.isInterpolating()) {
            this.interpolator.tick();
        } else {
            this.prevPos = this.pos.clone();
        }
    }

    public boolean isInterpolating() {
        return this.getInterpolator() != null && this.getInterpolator().isInterpolating();
    }

    public Box getBoundingBox() {
        return this.calculateBoundingBox();
    }

    public Box getBoundingBox(float f) {
        if (Math.abs(1 - f) <= 1.0E-3) {
            return this.calculateBoundingBox();
        }

        float x = MathUtil.lerp(f, this.prevPos.x, this.pos.x);
        float y = MathUtil.lerp(f, this.prevPos.y, this.pos.y);
        float z = MathUtil.lerp(f, this.prevPos.z, this.pos.z);
        return this.calculateBoundingBox(new Vec3(x, y, z));
    }

    public Box calculateBoundingBox() {
        return this.entity.getDimensions().getBoxAt(this.pos);
    }

    public Box calculateBoundingBox(Vec3 vec3) {
        return this.entity.getDimensions().getBoxAt(vec3);
    }

    public void setTeleportPos(Vec3 pos) {
        this.prevPos = this.pos = pos;
    }

    @Override
    public CachedEntityState clone() {
        final CachedEntityState state = new CachedEntityState(player, entity);
        state.setPos(this.pos.clone());
        state.setPrevPos(this.prevPos.clone());
        state.setInterpolator(this.interpolator == null ? null : this.interpolator.clone());

        return state;
    }
}
