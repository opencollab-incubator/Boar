package ac.boar.anticheat.collision;

import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class DepenetrationState {

    private static final float PENETRATION_THRESHOLD = 1.0E-6F;

    // Flag bits. Only completeMove sets them. No code sets 0x1 or 0x8, so no test reads those bits.
    // Set on a move with penetration.
    private static final int PENETRATED = 0x2;
    // Set on a move with penetration when PENETRATED was already set.
    private static final int CONSECUTIVELY_PENETRATED = 0x4;
    // The mover's PUSH_TOWARDS_CLOSEST_SPACE entity flag, as passed to the last completeMove.
    private static final int PUSH_TOWARDS_CLOSEST_SPACE = 0x10;

    private int flags;
    private Vec3 intrinsic;
    private final List<Box> oneWayBoxes = new ArrayList<>();
    private float lastPenetrationSum;

    public DepenetrationState() {
        this(new Vec3(0, 0, 0));
    }

    public DepenetrationState(final Vec3 intrinsic) {
        this.intrinsic = intrinsic.clone();
    }

    public int flags() {
        return this.flags;
    }

    public float lastPenetrationSum() {
        return this.lastPenetrationSum;
    }

    public boolean penetratedLastMove() {
        return this.lastPenetrationSum >= PENETRATION_THRESHOLD;
    }

    public boolean consecutivelyPenetrated() {
        return (this.flags & CONSECUTIVELY_PENETRATED) != 0;
    }

    public List<Box> oneWayBoxes() {
        return this.oneWayBoxes.stream().map(Box::clone).toList();
    }

    private void setFlag(final int flag, final boolean enabled) {
        this.flags = enabled ? this.flags | flag : this.flags & ~flag;
    }

    // nearby: how far nearby actors may push the mover out on this move (CompensatedWorldImpl.nearbyDepenetrationMagnitude), or null.
    public Vec3 maxPushOut(final Vec3 nearby) {
        final boolean noBaseAllowance = !this.oneWayBoxes.isEmpty() || (this.flags & (PUSH_TOWARDS_CLOSEST_SPACE | CONSECUTIVELY_PENETRATED)) != 0;
        final float base = noBaseAllowance ? 0 : 1;
        Vec3 allowed = maximum(this.intrinsic, new Vec3(base, base, base));
        return nearby != null ? maximum(allowed, nearby) : allowed;
    }

    // Called once for the first movement sweep. Auto-step tries use the allowance that is already set.
    public void completeMove(final float penetrationSum, final boolean pushTowardsClosestSpaceFlag) {
        this.lastPenetrationSum = penetrationSum;
        this.setFlag(PUSH_TOWARDS_CLOSEST_SPACE, pushTowardsClosestSpaceFlag);
        final boolean penetrated = penetrationSum >= PENETRATION_THRESHOLD;
        if (penetrated) {
            this.flags |= (this.flags & PENETRATED) == 0 ? PENETRATED : CONSECUTIVELY_PENETRATED;
        } else {
            this.flags &= ~(PENETRATED | CONSECUTIVELY_PENETRATED);
        }
    }

    public void onSizeChanged() {
        this.flags &= ~PENETRATED;
    }

    public void addOneWayBox(final Box box) {
        if (this.oneWayBoxes.stream().noneMatch(box::equals)) {
            this.oneWayBoxes.add(box.clone());
        }
    }

    public void removeSeparatedOneWayBoxes(final Box actorBox) {
        this.oneWayBoxes.removeIf(box -> !box.intersects(actorBox));
    }

    public DepenetrationState copy() {
        final DepenetrationState copy = new DepenetrationState();
        copy.restore(this);
        return copy;
    }

    // Copy again on every restore, so retrying from one snapshot cannot change that snapshot.
    public void restore(final DepenetrationState snapshot) {
        if (snapshot == this) {
            return;
        }
        this.flags = snapshot.flags;
        this.intrinsic = snapshot.intrinsic.clone();
        this.oneWayBoxes.clear();
        for (final Box box : snapshot.oneWayBoxes) {
            this.oneWayBoxes.add(box.clone());
        }
        this.lastPenetrationSum = snapshot.lastPenetrationSum;
    }

    private static Vec3 maximum(final Vec3 a, final Vec3 b) {
        return new Vec3(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
    }
}
