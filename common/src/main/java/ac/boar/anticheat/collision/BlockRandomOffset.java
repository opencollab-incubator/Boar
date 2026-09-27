package ac.boar.anticheat.collision;

// BlockRandomOffsetComponent::getRandomOffset
// Some blocks (bamboo, pointed dripstone) move their shape by a random amount that depends on the block position.
// TODO: Needs more testing for other platforms since some wrap the seed to 32 bits.
public final class BlockRandomOffset {
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long SILVER_RATIO = 0x6A09E667F3BCC909L;

    // One axis of the offset: a value from min to max, in "steps" even steps (0 steps means any value in between).
    public record Axis(float min, float max, int steps) {
        public static final Axis NONE = new Axis(0, 0, 0);
    }

    private BlockRandomOffset() {
    }

    // Returns the offset as {x, y, z}.
    public static float[] get(final int x, final int z, final boolean wideSeed, final Axis axisX, final Axis axisY, final Axis axisZ) {
        final long seed = seed(x, z, wideSeed);
        long s0 = mix(seed);
        long s1 = mix(seed + GOLDEN_GAMMA);
        if (s0 == 0 && s1 == 0) {
            s0 = GOLDEN_GAMMA;
            s1 = SILVER_RATIO;
        }

        // The client always takes three random numbers in order: X, Y, Z (Xoroshiro128++ nextFloat).
        final float[] draws = new float[3];
        for (int i = 0; i < draws.length; i++) {
            final long out = Long.rotateLeft(s0 + s1, 17) + s0;
            s1 ^= s0;
            s0 = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
            s1 = Long.rotateLeft(s1, 28);
            draws[i] = (float) (out >>> 40) * 5.9604645E-8F;
        }

        return new float[]{value(axisX, draws[0]), value(axisY, draws[1]), value(axisZ, draws[2])};
    }

    private static long seed(final int x, final int z, final boolean wideSeed) {
        // Windows multiplies the signed X coordinate in 64 bits. Android cuts the product to 32 bits, like Java's Mth.getSeed
        // Only Windows and Android are tested in game. The two only differ when |x| > 686. See NetworkSession#wideRandomOffsetSeed
        final long xPart = wideSeed ? 3129871L * (long) x : (long) (x * 3129871);
        final long v1 = xPart ^ (116129781L * (long) z);
        final long t = ((v1 * 42317861L + 11L) * v1) >>> 16;
        return ((long) (int) t) ^ SILVER_RATIO;
    }

    private static long mix(long v) {
        v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
        v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
        return v ^ (v >>> 31);
    }

    private static float value(final Axis axis, final float random) {
        if (!(axis.min() < axis.max())) {
            return axis.min();
        }
        if (axis.steps() == 1) {
            return (axis.min() + axis.max()) * 0.5F;
        }

        float range = axis.max() - axis.min();
        float fraction = random;
        if (axis.steps() != 0) {
            range = range / (float) (axis.steps() - 1);
            fraction = (float) Math.floor(random * (float) axis.steps());
        }
        return axis.min() + range * fraction;
    }
}
