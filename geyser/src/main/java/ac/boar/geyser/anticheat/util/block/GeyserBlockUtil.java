package ac.boar.geyser.anticheat.util.block;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.geyser.BlockEntityInfo;
import ac.boar.geyser.anticheat.data.block.GeyserBoarBlockState;
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
        GeyserBoarBlockState blockState = (GeyserBoarBlockState) player.compensatedWorld.getBlockState(position.north(), 0);
        GeyserBoarBlockState blockState2 = (GeyserBoarBlockState) player.compensatedWorld.getBlockState(position.east(), 0);
        GeyserBoarBlockState blockState3 = (GeyserBoarBlockState) player.compensatedWorld.getBlockState(position.south(), 0);
        GeyserBoarBlockState blockState4 = (GeyserBoarBlockState) player.compensatedWorld.getBlockState(position.west(), 0);

        boolean north = connectsTo(main, blockState, blockState.isFaceSturdy(player), Direction.SOUTH);
        boolean east = connectsTo(main, blockState2, blockState2.isFaceSturdy(player), Direction.WEST);
        boolean south = connectsTo(main, blockState3, blockState3.isFaceSturdy(player), Direction.NORTH);
        boolean west = connectsTo(main, blockState4, blockState4.isFaceSturdy(player), Direction.EAST);

        // A bit hacky but works, Geyser withValue implementation seems to be broken.
        String identifier = main.block().defaultBlockState().toString().intern();
        identifier = identifier.replace("north=true", "north=" + north);
        identifier = identifier.replace("east=true", "east=" + east);
        identifier = identifier.replace("south=true", "south=" + south);
        identifier = identifier.replace("west=true", "west=" + west);
        identifier = identifier.replace("waterlogged=true", "waterlogged=false");

        return BlockState.of(BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.getOrDefault(identifier, main.javaId()));
        //return main.block().defaultBlockState().withValue(EAST, east).withValue(NORTH, north).withValue(SOUTH, south).withValue(WATERLOGGED,false).withValue(WEST, west); this is broken, geyser fault I think?
    }

    public static BlockState findIronBarsBlockState(BoarPlayer player, BlockState state, Vector3i position) {
        GeyserBoarBlockState blockState = (GeyserBoarBlockState) player.compensatedWorld.getBlockState(position.north(), 0);
        GeyserBoarBlockState blockState2 = (GeyserBoarBlockState) player.compensatedWorld.getBlockState(position.south(), 0);
        GeyserBoarBlockState blockState3 = (GeyserBoarBlockState) player.compensatedWorld.getBlockState(position.west(), 0);
        GeyserBoarBlockState blockState4 = (GeyserBoarBlockState) player.compensatedWorld.getBlockState(position.east(), 0);

        boolean north = attachsTo(blockState, blockState.isFaceSturdy(player));
        boolean south = attachsTo(blockState2, blockState2.isFaceSturdy(player));
        boolean west = attachsTo(blockState3, blockState3.isFaceSturdy(player));
        boolean east = attachsTo(blockState4, blockState4.isFaceSturdy(player));

        // A bit hacky but works, Geyser withValue implementation seems to be broken.
        String identifier = state.block().defaultBlockState().toString().intern();
        identifier = identifier.replace("north=true", "north=" + north);
        identifier = identifier.replace("east=true", "east=" + east);
        identifier = identifier.replace("south=true", "south=" + south);
        identifier = identifier.replace("west=true", "west=" + west);
        identifier = identifier.replace("waterlogged=true", "waterlogged=false");

        return BlockState.of(BlockRegistries.JAVA_BLOCK_STATE_IDENTIFIER_TO_ID.getOrDefault(identifier, state.javaId()));
    }

    public static String getStairShape(BoarPlayer player, BlockState state, Vector3i pos) {
        Direction direction = state.getValue(HORIZONTAL_FACING);
        GeyserBoarBlockState boarState = (GeyserBoarBlockState) player.compensatedWorld.getBlockState(pos.add(direction.getUnitVector()), 0);
        BlockState blockState = boarState.getState();
        if (isStairs(boarState) && Objects.equals(state.getValue(HALF), blockState.getValue(HALF))) {
            Direction direction2 = blockState.getValue(HORIZONTAL_FACING);
            if (direction2.getAxis() != state.getValue(HORIZONTAL_FACING).getAxis() && isDifferentOrientation(player, state, pos, direction2.reversed())) {
                if (direction2 == GeyserDirectionUtil.rotateYCounterclockwise(direction)) {
                    return "outer_left";
                }

                return "outer_right";
            }
        }

        GeyserBoarBlockState boarState2 = (GeyserBoarBlockState) player.compensatedWorld.getBlockState(pos.add(direction.reversed().getUnitVector()), 0);
        BlockState blockState2 = boarState2.getState();
        if (isStairs(boarState2) && Objects.equals(state.getValue(HALF), blockState2.getValue(HALF))) {
            Direction direction3 = blockState2.getValue(HORIZONTAL_FACING);
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
        GeyserBoarBlockState boarState = (GeyserBoarBlockState) player.compensatedWorld.getBlockState(pos.add(dir.getUnitVector()), 0);
        BlockState blockState = boarState.getState();
        return !isStairs(boarState) || blockState.getValue(HORIZONTAL_FACING) != state.getValue(HORIZONTAL_FACING) || !Objects.equals(blockState.getValue(HALF), state.getValue(HALF));
    }

    private static boolean isStairs(GeyserBoarBlockState state) {
        return BlockMappings.get().getStairsBlocks().contains(state.block());
    }

    private static boolean connectsTo(BlockState blockState, GeyserBoarBlockState neighbour, boolean bl, Direction direction) {
        return !isExceptionForConnection(neighbour) && bl || isSameFence(neighbour, blockState) || connectsToDirection(neighbour, direction);
    }

    private static boolean attachsTo(GeyserBoarBlockState blockState, boolean bl) {
        boolean walls = BlockMappings.get().getWallBlocks().contains(blockState.block());
        return !isExceptionForConnection(blockState) && bl || blockState.getState().is(Blocks.IRON_BARS) || blockState.getState().toString().toLowerCase(Locale.ROOT).contains("glass_pane") || walls;
    }

    private static boolean isSameFence(GeyserBoarBlockState blockState, BlockState currentBlockState) {
        return BlockMappings.get().getFenceBlocks().contains(blockState.block()) && blockState.getState().is(Blocks.NETHER_BRICK_FENCE) == currentBlockState.is(Blocks.NETHER_BRICK_FENCE);
    }

    private static boolean connectsToDirection(GeyserBoarBlockState blockState, Direction direction) {
        if (!BlockMappings.get().getFenceGateBlocks().contains(blockState.block())) {
            return false;
        }

        return blockState.getState().getValue(HORIZONTAL_FACING).getAxis() == GeyserDirectionUtil.getClockWise(direction).getAxis();
    }

    private static boolean isExceptionForConnection(GeyserBoarBlockState blockState) {
        return BlockMappings.get().getLeavesBlocks().contains(blockState.block()) || blockState.getState().is(Blocks.BARRIER) ||
                blockState.getState().is(Blocks.CARVED_PUMPKIN) || blockState.getState().is(Blocks.JACK_O_LANTERN) ||
                blockState.getState().is(Blocks.MELON) || blockState.getState().is(Blocks.PUMPKIN)
                || BlockMappings.get().getShulkerBlocks().contains(blockState.block());
    }
}
