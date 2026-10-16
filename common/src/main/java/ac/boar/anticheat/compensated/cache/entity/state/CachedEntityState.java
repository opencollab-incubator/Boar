package ac.boar.anticheat.compensated.cache.entity.state;

import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.player.data.VehicleType;
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
    private Box simulatedBox;
    private EntityDimensions simulatedDimensions;
    private boolean locallySimulatedPose;

    public CachedEntityState(BoarPlayer player, EntityCache entity) {
        this.player = player;
        this.entity = entity;

        this.interpolator = new PositionInterpolator(this);
    }

    public void tick() {
        this.prevPos = this.pos.clone();
        if (this.interpolator != null) {
            // The client sets a boat's velocity to zero while interpolation (smooth movement toward a server position) has steps left.
            // This runs before the step, so the last step counts too.
            if (this.entity.getVehicleType() == VehicleType.BOAT && this.interpolator.isInterpolating()) {
                this.entity.setBoatVelocity(Vec3.ZERO.clone());
            }
            this.interpolator.tick();
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
        if (this.simulatedBox != null) {
            if (this.entity.getDimensions().equals(this.simulatedDimensions)) {
                return this.simulatedBox;
            }
            this.simulatedBox = null;
            this.simulatedDimensions = null;
        }
        return this.entity.getDimensions().getBoxAt(this.pos);
    }

    public Box calculateBoundingBox(Vec3 vec3) {
        return this.entity.getDimensions().getBoxAt(vec3);
    }

    public void setTeleportPos(Vec3 pos) {
        this.prevPos = this.pos = pos;
        this.simulatedBox = null;
        this.simulatedDimensions = null;
        this.locallySimulatedPose = false;
    }

    public void setPos(Vec3 pos) {
        if (this.pos.x != pos.x || this.pos.y != pos.y || this.pos.z != pos.z) {
            this.simulatedBox = null;
            this.simulatedDimensions = null;
            this.locallySimulatedPose = false;
        }
        this.pos = pos;
    }

    // Local control starts from the actor's pose. This includes kept box edges that cannot be rebuilt exactly from the center.
    public void setBoatControlPose(final Vec3 position, final Box box) {
        this.setPos(position.clone());
        this.simulatedBox = box;
        this.simulatedDimensions = this.entity.getDimensions();
        this.locallySimulatedPose = true;
    }

    // Only a finished boat simulation step sets this. Keep its moved box edges. They may not survive a trip through the center point.
    public void setSimulatedBoatState(Vec3 previous, Vec3 position, Box box) {
        if (box == null || !MathUtil.finite(previous) || !MathUtil.finite(position)
                || !Float.isFinite(box.minX) || !Float.isFinite(box.minY) || !Float.isFinite(box.minZ)
                || !Float.isFinite(box.maxX) || !Float.isFinite(box.maxY) || !Float.isFinite(box.maxZ)) {
            return;
        }
        this.prevPos = previous.clone();
        this.pos = position.clone();
        this.simulatedBox = box;
        this.simulatedDimensions = this.entity.getDimensions();
        this.locallySimulatedPose = true;
        // Client prediction clears remote interpolation (smooth movement toward server positions) while this actor is simulated locally.
        this.interpolator.clear();
    }

    @Override
    public CachedEntityState clone() {
        final CachedEntityState state = new CachedEntityState(player, entity);
        state.setPos(this.pos.clone());
        state.setPrevPos(this.prevPos.clone());
        state.setInterpolator(this.interpolator == null ? null : this.interpolator.clone());
        state.simulatedBox = this.simulatedBox;
        state.simulatedDimensions = this.simulatedDimensions;
        state.locallySimulatedPose = this.locallySimulatedPose;

        return state;
    }
}
