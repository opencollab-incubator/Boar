package ac.boar.anticheat.prediction.uncertainty;

import ac.boar.anticheat.util.math.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class UncertaintyEnvelope {
    private final boolean recordReasons;
    private float loX, hiX, loY, hiY, loZ, hiZ;
    private List<String> reasons;

    public UncertaintyEnvelope(final boolean recordReasons) {
        this.recordReasons = recordReasons;
    }

    public void widenX(final float lo, final float hi, final String reason) {
        this.widenX(lo, hi);
        this.record(reason);
    }

    public void widenY(final float lo, final float hi, final String reason) {
        this.widenY(lo, hi);
        this.record(reason);
    }

    public void widenZ(final float lo, final float hi, final String reason) {
        this.widenZ(lo, hi);
        this.record(reason);
    }

    public void widenSymmetric(final float x, final float y, final float z, final String reason) {
        this.widenX(-Math.abs(x), Math.abs(x));
        this.widenY(-Math.abs(y), Math.abs(y));
        this.widenZ(-Math.abs(z), Math.abs(z));
        this.record(reason);
    }

    public void includeDelta(final Vec3 altMinusPredicted, final String reason) {
        this.widenX(Math.min(0.0F, altMinusPredicted.x), Math.max(0.0F, altMinusPredicted.x));
        this.widenY(Math.min(0.0F, altMinusPredicted.y), Math.max(0.0F, altMinusPredicted.y));
        this.widenZ(Math.min(0.0F, altMinusPredicted.z), Math.max(0.0F, altMinusPredicted.z));
        this.record(reason);
    }

    public Vec3 excess(final Vec3 diff) {
        return new Vec3(
                this.excess(diff.x, this.loX, this.hiX),
                this.excess(diff.y, this.loY, this.hiY),
                this.excess(diff.z, this.loZ, this.hiZ)
        );
    }

    public float distanceOutside(final Vec3 diff) {
        return this.excess(diff).length();
    }

    public boolean contains(final Vec3 diff) {
        return diff.x >= this.loX && diff.x <= this.hiX
                && diff.y >= this.loY && diff.y <= this.hiY
                && diff.z >= this.loZ && diff.z <= this.hiZ;
    }

    public String describe() {
        final String description = "x=[" + this.loX + ", " + this.hiX + "]"
                + " y=[" + this.loY + ", " + this.hiY + "]"
                + " z=[" + this.loZ + ", " + this.hiZ + "]";
        if (this.reasons == null || this.reasons.isEmpty()) {
            return description;
        }
        return description + " reasons=" + this.reasons;
    }

    private float excess(final float diff, final float lo, final float hi) {
        if (diff < lo) {
            return lo - diff;
        }
        if (diff > hi) {
            return diff - hi;
        }
        return 0.0F;
    }

    private void widenX(final float lo, final float hi) {
        final float low = Math.min(lo, hi);
        final float high = Math.max(lo, hi);
        this.loX = Math.min(this.loX, low);
        this.hiX = Math.max(this.hiX, high);
    }

    private void widenY(final float lo, final float hi) {
        final float low = Math.min(lo, hi);
        final float high = Math.max(lo, hi);
        this.loY = Math.min(this.loY, low);
        this.hiY = Math.max(this.hiY, high);
    }

    private void widenZ(final float lo, final float hi) {
        final float low = Math.min(lo, hi);
        final float high = Math.max(lo, hi);
        this.loZ = Math.min(this.loZ, low);
        this.hiZ = Math.max(this.hiZ, high);
    }

    private void record(final String reason) {
        if (reason == null || !this.recordReasons) {
            return;
        }
        if (this.reasons == null) {
            this.reasons = new ArrayList<>();
        }
        if (!this.reasons.contains(reason)) {
            this.reasons.add(reason);
        }
    }
}
