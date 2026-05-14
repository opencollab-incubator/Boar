package ac.boar.geyser.anticheat.util.block;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.geyser.BlockEntityInfo;
import ac.boar.geyser.anticheat.data.block.GeyserBoarBlockStateDelegate;
import ac.boar.geyser.anticheat.util.math.GeyserDirectionUtil;
import ac.boar.mappings.block.BlockMappings;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.property.ChestType;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.Direction;
import org.geysermc.geyser.registry.BlockRegistries;

import java.util.Locale;
import java.util.Objects;

import static org.geysermc.geyser.level.block.property.Properties.CHEST_TYPE;
import static org.geysermc.geyser.level.block.property.Properties.HALF;
import static org.geysermc.geyser.level.block.property.Properties.HORIZONTAL_FACING;

public final class GeyserBlockUtil {

    // This is no longer accurate as of 1.21.110 because Mojang decide to break chest state even more yay!
    // There is so many FUCKING PROBLEMS WITH CHEST WTFFFFF WHY THE FUCK CHEST IS SO BROKEN AND NOW EVEN MORE BROKEN
    // AS OF 1.21.110 MOJANG????
    public static BlockState findChestState(final BoarPlayer player, final BlockState state, final Vector3i vector3i) {
        final BlockEntityInfo blockEntity = player.compensatedWorld.getBlockEntity(vector3i.getX(), vector3i.getY(), vector3i.getZ());
        if (blockEntity == null || blockEntity.nbt() == null) {
            return state.withValue(CHEST_TYPE, ChestType.SINGLE);
        }

        int pairX = blockEntity.nbt().getInt("pairx", 0);
        int pairZ = blockEntity.nbt().getInt("pairz", 0);
        if (Math.abs(vector3i.getX() - pairX) > 1 || Math.abs(vector3i.getZ() - pairZ) > 1) {
            return state.withValue(CHEST_TYPE, ChestType.SINGLE);
        }
        final Vector3i attachedPos = Vector3i.from(pairX, vector3i.getY(), pairZ);

        final int id = player.compensatedWorld.getBlockAt(attachedPos.getX(), attachedPos.getY(), attachedPos.getZ(), 0);
        if (BlockState.of(id).block().javaId() != state.block().javaId()) {
            return state.withValue(CHEST_TYPE, ChestType.SINGLE);
        }

        int pairLead = blockEntity.nbt().getInt("pairlead", 0);
        return state.withValue(CHEST_TYPE, pairLead == 1 ? ChestType.RIGHT : ChestType.LEFT);
    }

    public static BlockState findFenceBlockState(BoarPlayer player, BlockState main, Vector3i position) {
        BoarBlockState north = player.compensatedWorld.getBlockState(position.north(), 0);
        BoarBlockState east = player.compensatedWorld.getBlockState(position.east(), 0);
        BoarBlockState south = player.compensatedWorld.getBlockState(position.south(), 0);
        BoarBlockState west = player.compensatedWorld.getBlockState(position.west(), 0);

        boolean northConnect = connectsTo(main, north, north.isFaceSturdy(player), Direction.SOUTH);
        boolean eastConnect = connectsTo(main, east, east.isFaceSturdy(player), Direction.WEST);
        boolean southConnect = connectsTo(main, south, south.isFaceSturdy(player), Direction.NORTH);
        boolean westConnect = connectsTo(main, west, west.isFaceSturdy(player), Direction.EAST);

        // A bit hacky but works, Geyser withValue implementation seems to be broken.
        String identifier = main.block().defaultBlockState().toString().intern();
        identifier = identifier.replace("north=true", "north=" + northConnect);
        identifier = identifier.replace("east=true", "east=" + eastConnect);
        identifier = identifier.replace("south=true", "south=" + southConnect);
        identifier = identifier.replace("west=true", "west=" + westConnect);
        identifier = identifier.replace("waterlogged=true", "waterlogged=false");

        return BlockState.of(BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.getOrDefault(identifier, main.javaId()));
    }

    public static BlockState findIronBarsBlockState(BoarPlayer player, BlockState state, Vector3i position) {
        BoarBlockState north = player.compensatedWorld.getBlockState(position.north(), 0);
        BoarBlockState south = player.compensatedWorld.getBlockState(position.south(), 0);
        BoarBlockState west = player.compensatedWorld.getBlockState(position.west(), 0);
        BoarBlockState east = player.compensatedWorld.getBlockState(position.east(), 0);

        boolean northConnect = attachsTo(north, north.isFaceSturdy(player));
        boolean southConnect = attachsTo(south, south.isFaceSturdy(player));
        boolean westConnect = attachsTo(west, west.isFaceSturdy(player));
        boolean eastConnect = attachsTo(east, east.isFaceSturdy(player));

        // A bit hacky but works, Geyser withValue implementation seems to be broken.
        String identifier = state.block().defaultBlockState().toString().intern();
        identifier = identifier.replace("north=true", "north=" + northConnect);
        identifier = identifier.replace("east=true", "east=" + eastConnect);
        identifier = identifier.replace("south=true", "south=" + southConnect);
        identifier = identifier.replace("west=true", "west=" + westConnect);
        identifier = identifier.replace("waterlogged=true", "waterlogged=false");

        return BlockState.of(BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.getOrDefault(identifier, state.javaId()));
    }

    public static String getStairShape(BoarPlayer player, BlockState state, Vector3i pos) {
        Direction direction = state.getValue(HORIZONTAL_FACING);
        BoarBlockState ahead = player.compensatedWorld.getBlockState(pos.add(direction.getUnitVector()), 0);
        BlockState aheadState = GeyserBoarBlockStateDelegate.unwrap(ahead).getState();
        if (isStairs(ahead) && Objects.equals(state.getValue(HALF), aheadState.getValue(HALF))) {
            Direction direction2 = aheadState.getValue(HORIZONTAL_FACING);
            if (direction2.getAxis() != state.getValue(HORIZONTAL_FACING).getAxis() && isDifferentOrientation(player, state, pos, direction2.reversed())) {
                if (direction2 == GeyserDirectionUtil.rotateYCounterclockwise(direction)) {
                    return "outer_left";
                }

                return "outer_right";
            }
        }

        BoarBlockState behind = player.compensatedWorld.getBlockState(pos.add(direction.reversed().getUnitVector()), 0);
        BlockState behindState = GeyserBoarBlockStateDelegate.unwrap(behind).getState();
        if (isStairs(behind) && Objects.equals(state.getValue(HALF), behindState.getValue(HALF))) {
            Direction direction3 = behindState.getValue(HORIZONTAL_FACING);
            if (direction3.getAxis() != state.getValue(HORIZONTAL_FACING).getAxis() && isDifferentOrientation(player, state, pos, direction3)) {
                if (direction3 == GeyserDirectionUtil.rotateYCounterclockwise(direction)) {
                    return "inner_left";
                }

                return "inner_right";
            }
        }

        return "straight";
    }

    private static boolean isDifferentOrientation(BoarPlayer player, BlockState state, Vector3i pos, Direction dir) {
        BoarBlockState neighbour = player.compensatedWorld.getBlockState(pos.add(dir.getUnitVector()), 0);
        BlockState neighbourState = GeyserBoarBlockStateDelegate.unwrap(neighbour).getState();
        return !isStairs(neighbour) || neighbourState.getValue(HORIZONTAL_FACING) != state.getValue(HORIZONTAL_FACING) || !Objects.equals(neighbourState.getValue(HALF), state.getValue(HALF));
    }

    private static boolean isStairs(BoarBlockState state) {
        return BlockMappings.get().getStairsBlocks().contains(state.block());
    }

    private static boolean connectsTo(BlockState main, BoarBlockState neighbour, boolean faceSturdy, Direction direction) {
        return !isExceptionForConnection(neighbour) && faceSturdy || isSameFence(neighbour, main) || connectsToDirection(neighbour, direction);
    }

    private static boolean attachsTo(BoarBlockState neighbour, boolean faceSturdy) {
        boolean walls = BlockMappings.get().getWallBlocks().contains(neighbour.block());
        BlockState raw = GeyserBoarBlockStateDelegate.unwrap(neighbour).getState();
        return !isExceptionForConnection(neighbour) && faceSturdy || raw.is(Blocks.IRON_BARS) || raw.toString().toLowerCase(Locale.ROOT).contains("glass_pane") || walls;
    }

    private static boolean isSameFence(BoarBlockState neighbour, BlockState main) {
        BlockState raw = GeyserBoarBlockStateDelegate.unwrap(neighbour).getState();
        return BlockMappings.get().getFenceBlocks().contains(neighbour.block()) && raw.is(Blocks.NETHER_BRICK_FENCE) == main.is(Blocks.NETHER_BRICK_FENCE);
    }

    private static boolean connectsToDirection(BoarBlockState neighbour, Direction direction) {
        if (!BlockMappings.get().getFenceGateBlocks().contains(neighbour.block())) {
            return false;
        }

        BlockState raw = GeyserBoarBlockStateDelegate.unwrap(neighbour).getState();
        return raw.getValue(HORIZONTAL_FACING).getAxis() == GeyserDirectionUtil.getClockWise(direction).getAxis();
    }

    private static boolean isExceptionForConnection(BoarBlockState neighbour) {
        BlockState raw = GeyserBoarBlockStateDelegate.unwrap(neighbour).getState();
        return BlockMappings.get().getLeavesBlocks().contains(neighbour.block()) || raw.is(Blocks.BARRIER) ||
                raw.is(Blocks.CARVED_PUMPKIN) || raw.is(Blocks.JACK_O_LANTERN) ||
                raw.is(Blocks.MELON) || raw.is(Blocks.PUMPKIN)
                || BlockMappings.get().getShulkerBlocks().contains(neighbour.block());
    }
}
