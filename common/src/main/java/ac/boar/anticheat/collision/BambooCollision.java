package ac.boar.anticheat.collision;

import ac.boar.anticheat.util.math.Box;

// See BambooStalkBlock::getVisualShape, BlockRandomOffsetComponent::getRandomOffset, and BlockType::getCollisionShape
public final class BambooCollision {
    private static final Box THIN_SHAPE = new Box(0.5F, 0, 0.5F, 0.625F, 1, 0.625F);
    private static final Box THICK_SHAPE = new Box(0.5F, 0, 0.5F, 0.6875F, 1, 0.6875F);

    private static final float OFFSET_MIN = -0.25F;
    private static final float OFFSET_MAX = 0.25F;
    private static final int OFFSET_STEPS = 16;

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long SILVER_RATIO = 0x6A09E667F3BCC909L;

    private BambooCollision() {
    }

    public static Box getCollisionBox(final int x, final int y, final int z, final boolean thick) {
        final Box shape = thick ? THICK_SHAPE : THIN_SHAPE;
        final long seed = seed(x, z);
        long s0 = mix(seed);
        long s1 = mix(seed + GOLDEN_GAMMA);
        if (s0 == 0 && s1 == 0) {
            s0 = GOLDEN_GAMMA;
            s1 = SILVER_RATIO;
        }

        final float[] draws = new float[3];
        for (int i = 0; i < draws.length; i++) {
            final long out = Long.rotateLeft(s0 + s1, 17) + s0;
            s1 ^= s0;
            s0 = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
            s1 = Long.rotateLeft(s1, 28);
            draws[i] = (float) (out >>> 40) * 5.9604645E-8F;
        }

        final float fx = (float) x + quantize(draws[0]);
        final float fy = (float) y;
        final float fz = (float) z + quantize(draws[2]);
        return new Box(shape.minX + fx, shape.minY + fy, shape.minZ + fz, shape.maxX + fx, shape.maxY + fy, shape.maxZ + fz);
    }

    private static long seed(final int x, final int z) {
        // The Windows client multiplies the signed X coordinate in 64 bits. Do not truncate the product to 32 bits.
        final long v1 = (3129871L * (long) x) ^ (116129781L * (long) z);
        final long t = ((v1 * 42317861L + 11L) * v1) >>> 16;
        return ((long) (int) t) ^ SILVER_RATIO;
    }

    private static long mix(long v) {
        v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
        v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
        return v ^ (v >>> 31);
    }

    private static float quantize(final float random) {
        final float step = (OFFSET_MAX - OFFSET_MIN) / (float) (OFFSET_STEPS - 1);
        final float index = (float) Math.floor(random * (float) OFFSET_STEPS);
        return OFFSET_MIN + index * step;
    }
}
