package ac.boar.anticheat.data.block;

import ac.boar.anticheat.data.FluidState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.Reference;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Mutable;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.Property;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;

import java.util.List;

public interface BoarBlockState {
    int intermediaryId();

    Block block();

    BlockDefinition definition(BoarPlayer player);

    <T extends Comparable<T>> BoarBlockState with(Property<T> property, T value);

    boolean isWaterlogged();

    boolean isFaceSturdy(BoarPlayer player);

    boolean isAir();

    void onSteppedOn(BoarPlayer player, Vector3i vector3i);

    boolean blocksMotion(BoarPlayer player);

    void entityInside(BoarPlayer player, Mutable pos);

    default boolean isSolid(BoarPlayer player) {
        List<Box> boxes = findCollision(player, Vector3i.ZERO, Box.EMPTY, false);
        if (boxes.isEmpty()) {
            return false;
        } else {
            Box box = new Box(0, 0, 0, 0, 0, 0);
            for (Box box1 : boxes) {
                box = box1.union(box);
            }

            return box.getAverageSideLength() >= 0.7291666666666666 || box.getLengthY() >= 1.0;
        }
    }

    void updateEntityMovementAfterFallOn(BoarPlayer player, boolean living);

    List<Box> findCollision(BoarPlayer player, Vector3i pos, Box playerAABB, boolean checkAAB);

    List<Box> getCollisionBoxes();

    float getJumpFactor();

    float getFriction();

    float getBlockBounciness();

    Vector3i getPosition();

    int getLayer();

    FluidState getFluidState(int level);

    boolean is(Block block);

    boolean is(Reference<Block> block);

    <T extends Comparable<T>> T get(Property<T> property);

    static BoarBlockState create(int blockId, Vector3i pos, int layer) {
        return BoarBlockStateInst.factory().create(blockId, pos, layer);
    }
}
