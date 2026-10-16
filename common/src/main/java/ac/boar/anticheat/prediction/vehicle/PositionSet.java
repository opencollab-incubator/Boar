package ac.boar.anticheat.prediction.vehicle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// The places where the client could be showing a vehicle the server moves. We don't know the client's packet times or clock, so it is a set, not a point.
// FollowerEnvelope builds these sets (its header has the client sources). Users: the boat takeover (UncertainRunner.boatTakeoverShift and
// boatTakeoverCenterShift, via FollowerHistory.envelopeControlSet), BoatTopRetry, and the horse mount (HorseTicker.mountNear).
//
// Model: a center plus "pieces" (short line segments). A point is center + t1*piece1 + t2*piece2 + ..., each t between -1 and 1.
// (Math name: a zonotope. It is cheap to combine, and the inside test is exact.)
// - Target: on the vehicle's path between our earliest and latest guess of the client's lookup time. That path is a few pieces (path).
// - Shown position: each step moves w = 1/remaining of the way to the target: shown = (1 - w) * shown + w * target (mixInPlace).
public final class PositionSet {
    // Most pieces we keep. The smallest extra pieces are folded into one box.
    private static final int MAX_PIECES = 12;

    private float cx, cy, cz;
    private final List<float[]> pieces = new ArrayList<>();

    private PositionSet(final float x, final float y, final float z) {
        this.cx = x;
        this.cy = y;
        this.cz = z;
    }

    // Float precision near this point: a few steps of the float grid, so rounding in the seat math is not counted as outside.
    public static float slack(final float x, final float y, final float z) {
        return 4 * Math.max(Math.ulp(x), Math.max(Math.ulp(y), Math.ulp(z)));
    }

    public static PositionSet point(final float x, final float y, final float z) {
        return new PositionSet(x, y, z);
    }

    // A path through these points, in order. Each point is (x, y, z, ...). The shape holds every point on the path.
    public static PositionSet path(final List<float[]> points) {
        final float[] first = points.get(0), last = points.get(points.size() - 1);
        final PositionSet set = new PositionSet((first[0] + last[0]) * .5F, (first[1] + last[1]) * .5F, (first[2] + last[2]) * .5F);
        for (int i = 1; i < points.size(); i++) {
            final float[] a = points.get(i - 1), b = points.get(i);
            set.add((b[0] - a[0]) * .5F, (b[1] - a[1]) * .5F, (b[2] - a[2]) * .5F);
        }
        set.reduce();
        return set;
    }

    public PositionSet copy() {
        final PositionSet set = new PositionSet(this.cx, this.cy, this.cz);
        for (final float[] piece : this.pieces) {
            set.pieces.add(piece.clone());
        }
        return set;
    }

    // this = (1 - w) * this + w * other, for any point of one paired with any point of the other.
    // So the result can be bigger than the real set, but it never leaves out a real position.
    public void mixInPlace(final PositionSet other, final float w) {
        final float keep = 1.0F - w;
        this.cx = this.cx * keep + other.cx * w;
        this.cy = this.cy * keep + other.cy * w;
        this.cz = this.cz * keep + other.cz * w;
        for (final float[] piece : this.pieces) {
            piece[0] *= keep;
            piece[1] *= keep;
            piece[2] *= keep;
        }
        for (final float[] piece : other.pieces) {
            this.add(piece[0] * w, piece[1] * w, piece[2] * w);
        }
        this.reduce();
    }

    // The box around this shape and the other: one piece per axis. It holds every point of both.
    // Two equal shapes give the same box, so repeated hulls do not grow on their own.
    public PositionSet hull(final PositionSet other) {
        final float minX = Math.min(this.minX(), other.minX()), maxX = Math.max(this.maxX(), other.maxX());
        final float minY = Math.min(this.minY(), other.minY()), maxY = Math.max(this.maxY(), other.maxY());
        final float minZ = Math.min(this.minZ(), other.minZ()), maxZ = Math.max(this.maxZ(), other.maxZ());
        final PositionSet set = new PositionSet((minX + maxX) * .5F, (minY + maxY) * .5F, (minZ + maxZ) * .5F);
        set.add((maxX - minX) * .5F, 0, 0);
        set.add(0, (maxY - minY) * .5F, 0);
        set.add(0, 0, (maxZ - minZ) * .5F);
        return set;
    }

    public float centerX() {
        return this.cx;
    }

    public float centerY() {
        return this.cy;
    }

    public float centerZ() {
        return this.cz;
    }

    public float minX() {
        return this.cx - this.reach(0);
    }

    public float maxX() {
        return this.cx + this.reach(0);
    }

    public float minZ() {
        return this.cz - this.reach(2);
    }

    public float maxZ() {
        return this.cz + this.reach(2);
    }

    private void add(final float x, final float y, final float z) {
        if (x != 0 || y != 0 || z != 0) {
            this.pieces.add(new float[]{x, y, z});
        }
    }

    // Too many pieces: fold the smallest ones into a box (one piece per axis). The box holds them, so the shape only grows.
    private void reduce() {
        if (this.pieces.size() <= MAX_PIECES) {
            return;
        }
        this.pieces.sort(Comparator.comparingDouble(p -> p[0] * p[0] + p[1] * p[1] + p[2] * p[2]));
        float bx = 0, by = 0, bz = 0;
        final int fold = this.pieces.size() - (MAX_PIECES - 3);
        for (int i = 0; i < fold; i++) {
            final float[] piece = this.pieces.get(i);
            bx += Math.abs(piece[0]);
            by += Math.abs(piece[1]);
            bz += Math.abs(piece[2]);
        }
        this.pieces.subList(0, fold).clear();
        this.add(bx, 0, 0);
        this.add(0, by, 0);
        this.add(0, 0, bz);
    }

    // Half the size of the shape along one axis.
    private float reach(final int axis) {
        float sum = 0;
        for (final float[] piece : this.pieces) {
            sum += Math.abs(piece[axis]);
        }
        return sum;
    }

    public float minY() {
        return this.cy - this.reach(1);
    }

    public float maxY() {
        return this.cy + this.reach(1);
    }

    // Twice the sum of the piece lengths: the longest line through the shape is at most this. For a straight path it is the path length.
    public float length() {
        float sum = 0;
        for (final float[] piece : this.pieces) {
            sum += (float) Math.sqrt(piece[0] * piece[0] + piece[1] * piece[1] + piece[2] * piece[2]);
        }
        return 2 * sum;
    }

    /**
     * How far the point is outside the shape. 0 or less means inside.
     * Slack (float precision) is added on each axis, so a flat shape still has some size.
     * Exact test: a point is inside only if it is inside every face. Face directions are cross products of two pieces (the slack axes count as pieces).
     * Outside, the answer is the largest distance past one face. It is never more than the true distance.
     */
    public float excess(final float px, final float py, final float pz, final float slack) {
        final List<float[]> all = new ArrayList<>(this.pieces);
        all.add(new float[]{slack, 0, 0});
        all.add(new float[]{0, slack, 0});
        all.add(new float[]{0, 0, slack});
        final double dx = px - this.cx, dy = py - this.cy, dz = pz - this.cz;
        double worst = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                final float[] a = all.get(i), b = all.get(j);
                double nx = (double) a[1] * b[2] - (double) a[2] * b[1];
                double ny = (double) a[2] * b[0] - (double) a[0] * b[2];
                double nz = (double) a[0] * b[1] - (double) a[1] * b[0];
                final double norm = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (norm < 1.0E-12) {
                    continue;
                }
                nx /= norm;
                ny /= norm;
                nz /= norm;
                double size = 0;
                for (final float[] piece : all) {
                    size += Math.abs(nx * piece[0] + ny * piece[1] + nz * piece[2]);
                }
                final double distance = Math.abs(nx * dx + ny * dy + nz * dz);
                worst = Math.max(worst, distance - size);
            }
        }
        return (float) worst;
    }

    @Override
    public String toString() {
        return "center=(" + this.cx + "," + this.cy + "," + this.cz + ") pieces=" + this.pieces.size() + " length=" + this.length()
                + " y=[" + this.minY() + "," + this.maxY() + "]";
    }
}
