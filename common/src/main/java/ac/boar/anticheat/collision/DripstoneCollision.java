package ac.boar.anticheat.collision;

import ac.boar.anticheat.util.math.Box;

// See SpeleothemBlock::getVisualShape, BlockRandomOffsetComponent::getRandomOffset, and BlockType::getCollisionShape
public final class DripstoneCollision {
    // SpeleothemBlock::getVisualShape (the boxes are built in __GLOBAL__sub_I_unity_0_cxx.cxx)
    private static final Box TIP_STANDING_SHAPE = new Box(0.3125F, 0, 0.3125F, 0.6875F, 0.6875F, 0.6875F);
    private static final Box TIP_HANGING_SHAPE = new Box(0.3125F, 0.3125F, 0.3125F, 0.6875F, 1, 0.6875F);
    private static final Box FRUSTUM_SHAPE = new Box(0.25F, 0, 0.25F, 0.75F, 1, 0.75F);
    private static final Box MIDDLE_SHAPE = new Box(0.1875F, 0, 0.1875F, 0.8125F, 1, 0.8125F);
    private static final Box BASE_SHAPE = new Box(0.125F, 0, 0.125F, 0.875F, 1, 0.875F);
    private static final Box MERGE_SHAPE = new Box(0.3125F, 0, 0.3125F, 0.6875F, 1, 0.6875F);

    // VanillaBlockTypes::registerBlocks gives pointed dripstone its own offset: X and Z from -0.125 to 0.125 in 16 steps.
    private static final BlockRandomOffset.Axis OFFSET = new BlockRandomOffset.Axis(-0.125F, 0.125F, 16);

    private DripstoneCollision() {
    }

    // thickness uses the Java names (tip_merge, tip, frustum, middle, base). hanging is true when the tip points down.
    public static Box getCollisionBox(final int x, final int y, final int z, final boolean wideSeed, final String thickness, final boolean hanging) {
        final Box shape = switch (thickness) {
            case "tip" -> hanging ? TIP_HANGING_SHAPE : TIP_STANDING_SHAPE;
            case "frustum" -> FRUSTUM_SHAPE;
            case "middle" -> MIDDLE_SHAPE;
            case "base" -> BASE_SHAPE;
            default -> MERGE_SHAPE; // tip_merge
        };
        final float[] offset = BlockRandomOffset.get(x, z, wideSeed, OFFSET, BlockRandomOffset.Axis.NONE, OFFSET);

        // BlockType::getCollisionShape adds the offset to the block position first, then the shape, all in float.
        final float fx = (float) x + offset[0];
        final float fy = (float) y;
        final float fz = (float) z + offset[2];
        return new Box(shape.minX + fx, shape.minY + fy, shape.minZ + fz, shape.maxX + fx, shape.maxY + fy, shape.maxZ + fz);
    }
}
