package ac.boar.anticheat.collision;

import ac.boar.anticheat.util.math.Box;

// See BambooStalkBlock::getVisualShape, BlockRandomOffsetComponent::getRandomOffset, and BlockType::getCollisionShape
public final class BambooCollision {
    private static final Box THIN_SHAPE = new Box(0.5F, 0, 0.5F, 0.625F, 1, 0.625F);
    private static final Box THICK_SHAPE = new Box(0.5F, 0, 0.5F, 0.6875F, 1, 0.6875F);

    private static final BlockRandomOffset.Axis OFFSET = new BlockRandomOffset.Axis(-0.25F, 0.25F, 16);

    private BambooCollision() {
    }

    public static Box getCollisionBox(final int x, final int y, final int z, final boolean wideSeed, final boolean thick) {
        final Box shape = thick ? THICK_SHAPE : THIN_SHAPE;
        final float[] offset = BlockRandomOffset.get(x, z, wideSeed, OFFSET, BlockRandomOffset.Axis.NONE, OFFSET);

        final float fx = (float) x + offset[0];
        final float fy = (float) y;
        final float fz = (float) z + offset[2];
        return new Box(shape.minX + fx, shape.minY + fy, shape.minZ + fz, shape.maxX + fx, shape.maxY + fy, shape.maxZ + fz);
    }
}
