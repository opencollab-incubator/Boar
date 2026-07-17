package ac.boar.anticheat.collision;

import ac.boar.anticheat.data.block.BoarBlockState;
import ac.boar.anticheat.data.inventory.BoarItemStack;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Axis;
import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Direction;
import ac.boar.mappings.block.BlockMappings;
import ac.boar.mappings.block.Blocks;
import ac.boar.mappings.block.Properties;
import ac.boar.mappings.item.Items;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;

import java.util.ArrayList;
import java.util.List;

// Patch collision in bedrock that is different from java, or block with dynamic collision (ex: scaffolding)
public class BedrockCollision {
    protected final static List<Box> EMPTY_SHAPE = List.of();
    protected final static List<Box> SOLID_SHAPE = List.of(new Box(0, 0, 0, 1, 1, 1));
    
    protected final static List<Box> BED_SHAPE = List.of(new Box(0, 0, 0, 1, 0.5625F, 1));
    protected final static List<Box> HONEY_SHAPE = List.of(new Box(0.0625F, 0, 0.0625F, 0.9375F, 1, 0.9375F));
    protected final static List<Box> LECTERN_SHAPE = List.of(new Box(0, 0, 0, 1, 0.9F, 1));
    protected final static List<Box> CONDUIT_SHAPE = List.of(new Box(0.25F, 0, 0.25F, 0.75f, 0.5F, 0.75f));
    protected final static List<Box> CACTUS_SHAPE = List.of(new Box(0.0625F, 0, 0.0625F, 0.9375F, 1, 0.9375F));

    // Chest
    protected final static List<Box> SINGLE_CHEST_SHAPE = List.of(new Box(0.025F, 0, 0.025F, 0.975F, 0.95F, 0.975F));

    // Scaffolding
    protected final static List<Box> SCAFFOLDING_NORMAL_SHAPE;

    // Cauldron
    protected final static List<Box> CAULDRON_SHAPE;

    // Trapdoor
    protected final static List<Box> TRAPDOOR_EAST_SHAPE = List.of(new Box(0, 0, 0, 0.1825F, 1, 1));
    protected final static List<Box> TRAPDOOR_WEST_SHAPE = List.of(new Box(0.8175F, 0, 0, 1, 1, 1));
    protected final static List<Box> TRAPDOOR_SOUTH_SHAPE = List.of(new Box(0, 0, 0, 1, 1, 0.1825F));
    protected final static List<Box> TRAPDOOR_NORTH_SHAPE = List.of(new Box(0, 0, 0.8175F, 1, 1, 1));
    protected final static List<Box> TRAPDOOR_OPEN_BOTTOM_SHAPE = List.of(new Box(0, 0, 0, 1, 0.1825F, 1));
    protected final static List<Box> TRAPDOOR_OPEN_TOP_SHAPE = List.of(new Box(0, 0.8175F, 0, 1, 1, 1));

    // Door
    protected final static List<Box> DOOR_NORTH_SHAPE = List.of(new Box(0, 0, 0, 1, 1, 0.1825F));
    protected final static List<Box> DOOR_SOUTH_SHAPE = List.of(new Box(0, 0, 0.8175F, 1, 1, 1));
    protected final static List<Box> DOOR_EAST_SHAPE = List.of(new Box(0.8175F, 0, 0, 1, 1, 1));
    protected final static List<Box> DOOR_WEST_SHAPE = List.of(new Box(0, 0, 0, 0.1825F, 1, 1));

    protected final static List<Box> LANTERN_SHAPE = List.of(new Box(0.3125F, 0, 0.3125F, 0.6875F, 0.5F, 0.6875F));
    protected final static List<Box> HANGING_LANTERN_SHAPE = List.of(new Box(0.3125F, 0.125F, 0.3125F, 0.6875F, 0.625F, 0.6875F));

    protected final static List<Box> ANVIL_X_SHAPE = List.of(new Box(0.0F, 0.0F, 0.125F, 1.0F, 1.0F, 0.875F));
    protected final static List<Box> ANVIL_OTHER_SHAPE = List.of(new Box(0.125F, 0.0F, 0.0F, 0.875F, 1.0F, 1.0F));

    protected final static List<Box> FALLING_POWDER_SNOW_SNOW = List.of(new Box(0.0F, 0.0F, 0.0F, 0.0625F, 0.05625F, 0.0625F));

    protected final static List<Box> END_PORTAL_FRAME_SHAPE = List.of(new Box(0, 0, 0, 1, 0.8125F, 1));

    protected final static List<Box> TURTLE_EGG_SHAPE = List.of(new Box(0.2F, 0, 0.2F, 0.8F, 0.45F, 0.8F));

    static {
        // Scaffolding
        {
            Box lv = new Box(0, 0.875f, 0, 1, 1, 1);
            Box lv2 = new Box(0, 0, 0, 0.125f, 1, 0.125f);
            Box lv3 = new Box(0.875f, 0, 0, 1, 1, 0.125f);
            Box lv4 = new Box(0, 0, 0.875f, 0.125f, 1, 1);
            Box lv5 = new Box(0.875f, 0, 0.875f, 1, 1, 1);
            SCAFFOLDING_NORMAL_SHAPE = List.of(lv, lv2, lv3, lv4, lv5);
        }

        // Cauldron
        {
            float f = 0.125F;
            List<Box> boxes = new ArrayList<>();
            boxes.add(new Box(0.0F, 0.0F, 0.0F, 1.0F, 0.3125F, 1.0F));
            boxes.add(new Box(0.0F, 0.0F, 0.0F, f, 1.0F, 1.0F));
            boxes.add(new Box(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, f));
            boxes.add(new Box(1.0F - f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F));
            boxes.add(new Box(0.0F, 0.0F, 1.0F - f, 1.0F, 1.0F, 1.0F));
            CAULDRON_SHAPE = boxes;
        }
    }
    
    public static List<Box> buildThinBarsShape(boolean north, boolean east, boolean south, boolean west) {
        if (!north && !east && !south && !west) {
            return List.of(new Box(0.4375F, 0, 0.4375F, 0.5625F, 1, 0.5625F));
        }
        List<Box> boxes = new ArrayList<>();
        if (north) boxes.add(new Box(0.4375F, 0, 0,      0.5625F, 1, 0.5F));
        if (south) boxes.add(new Box(0.4375F, 0, 0.5F,   0.5625F, 1, 1));
        if (west)  boxes.add(new Box(0,       0, 0.4375F, 0.5F,   1, 0.5625F));
        if (east)  boxes.add(new Box(0.5F,    0, 0.4375F, 1,      1, 0.5625F));
        return boxes;
    }

    public static List<Box> getCollisionBox(final BoarPlayer player, final Box box, final Vector3i vector3i, final BoarBlockState state) {
        if (vector3i.getY() == player.compensatedWorld.getDimension().minY() - 41) {
            return SOLID_SHAPE;
        }

        if (state.is(Blocks.BELL) && state.get(Properties.BELL_ATTACHMENT).equals("floor")) {
            List<Box> collisions = new ArrayList<>();
            for (Box collisionBox : state.getCollisionBoxes()) {
                collisions.add(collisionBox.withMaxY(collisionBox.maxY - 0.1875F));
            }

            return collisions;
        }

        if (state.is(Blocks.BAMBOO)) {
//            Box baseShape = state.getValue(Properties.BAMBOO_LEAVES).equals("large") ? new Box(0, 0, 0, 0.1875F, 1, 0.1875F) : new Box(0, 0, 0, 0.125F, 1, 0.125F);

            // Couldn't they just keep the bamboo offsetting pre 1.21.80 but nope they changed it, and now I have no idea how it works?
            // Let's try to hack around this, if player is not colliding or only colliding horizontally then look at
            // UncertainRunner#extraOffsetNonTickEnd and EntityTicker#doSelfMove, for VERTICAL_COLLISION we can do a bit tricky hack to ensure accurate motion.
            if (!player.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION) || box == null) {
                return EMPTY_SHAPE;
            }

            // Workaround.... we can still ensure player won't be having weird y motion on bamboo using the VERTICAL_COLLISION input.
            Box solidOffset = SOLID_SHAPE.getFirst().offset(vector3i.getX(), vector3i.getY(), vector3i.getZ());

            // If player claimed to have y collision and their feet/head does hit something then we can be sure it's correct.
            // Also, this bamboo should not collide with player horizontal collision, only vertical so we can handle it properly.
            boolean likelyYCollision = solidOffset.calculateMaxDistance(Axis.Y, player.boundingBox, player.velocity.y) != player.velocity.y;
            return likelyYCollision && solidOffset.intersects(box) ? SOLID_SHAPE : EMPTY_SHAPE;
        }

        if (state.is(Blocks.POINTED_DRIPSTONE)) {
            // Like bamboo
            if (!player.getInputData().contains(PlayerAuthInputData.VERTICAL_COLLISION) || box == null) {
                return EMPTY_SHAPE;
            }

            List<Box> javaBoxes = state.getCollisionBoxes();
            if (javaBoxes.isEmpty()) {
                return EMPTY_SHAPE;
            }

            float minY = 1f, maxY = 0f;
            for (Box b : javaBoxes) {
                minY = Math.min(minY, b.minY);
                maxY = Math.max(maxY, b.maxY);
            }

            List<Box> vertical = List.of(new Box(0, minY, 0, 1, maxY, 1));
            Box verticalOffset = vertical.getFirst().offset(vector3i.getX(), vector3i.getY(), vector3i.getZ());
            boolean likelyYCollision = verticalOffset.calculateMaxDistance(Axis.Y, player.boundingBox, player.velocity.y) != player.velocity.y;
            return likelyYCollision && verticalOffset.intersects(box) ? vertical : EMPTY_SHAPE;
        }

        if (state.is(Blocks.END_PORTAL_FRAME)) {
            return END_PORTAL_FRAME_SHAPE;
        }

        if (state.is(Blocks.POWDER_SNOW)) {
            boolean leatherBoostOn = BoarItemStack.of(player.getSession(), player.compensatedInventory.armorContainer.get(3).getData()).is(Items.LEATHER_BOOTS);
            if (leatherBoostOn && player.position.y > vector3i.getY() + 1 - 1.0E-5f && !(player.getInputData().contains(PlayerAuthInputData.SNEAKING)|| player.getInputData().contains(PlayerAuthInputData.DESCEND_BLOCK))) {
                return SOLID_SHAPE;
            }
        }

        if (BlockMappings.get().getAnvilBlocks().contains(state.block())) {
            Direction direction = state.get(Properties.HORIZONTAL_FACING);
            if (direction.getAxis() == Axis.X) {
                return ANVIL_X_SHAPE;
            } else {
                return ANVIL_OTHER_SHAPE;
            }
        }

        if (BlockMappings.get().getLanternBlocks().contains(state.block())) {
            return Boolean.TRUE.equals(state.get(Properties.HANGING)) ? HANGING_LANTERN_SHAPE : LANTERN_SHAPE;
        }

        if (state.is(Blocks.ENDER_CHEST)) {
            return SINGLE_CHEST_SHAPE;
        }

        if (state.is(Blocks.SEA_PICKLE)) {
            return EMPTY_SHAPE;
        }

        if (state.is(Blocks.TURTLE_EGG)) {
            return TURTLE_EGG_SHAPE;
        }

        if (state.is(Blocks.DRAGON_EGG)) {
            return SOLID_SHAPE;
        }

        if (BlockMappings.get().getBedBlocks().contains(state.block())) {
            return BED_SHAPE;
        }

        if (state.is(Blocks.HONEY_BLOCK)) {
            return HONEY_SHAPE;
        }

        if (state.is(Blocks.LECTERN)) {
            return LECTERN_SHAPE;
        }

        if (BlockMappings.get().getCauldronBlocks().contains(state.block())) {
            return CAULDRON_SHAPE;
        }

        if (state.is(Blocks.CONDUIT)) {
            return CONDUIT_SHAPE;
        }

        if (state.is(Blocks.CACTUS)) {
            return CACTUS_SHAPE;
        }

        if (BlockMappings.get().getChestBlocks().contains(state.block())) {
            return SINGLE_CHEST_SHAPE;
        }

        if (BlockMappings.get().getTrapDoorBlocks().contains(state.block())) {
            if (!state.get(Properties.OPEN)) {
                return state.get(Properties.HALF).equalsIgnoreCase("top") ? TRAPDOOR_OPEN_TOP_SHAPE : TRAPDOOR_OPEN_BOTTOM_SHAPE;
            } else {
                switch (state.get(Properties.HORIZONTAL_FACING)) {
                    case SOUTH -> {
                        return TRAPDOOR_SOUTH_SHAPE;
                    }
                    case WEST -> {
                        return TRAPDOOR_WEST_SHAPE;
                    }
                    case EAST -> {
                        return TRAPDOOR_EAST_SHAPE;
                    }
                    default -> {
                        return TRAPDOOR_NORTH_SHAPE;
                    }
                }
            }
        }

        if (BlockMappings.get().getDoorBlocks().contains(state.block())) {
            Direction direction = state.get(Properties.HORIZONTAL_FACING);
            boolean bl = !state.get(Properties.OPEN);
            boolean bl2 = state.get(Properties.DOOR_HINGE).equalsIgnoreCase("right");

            switch (direction) {
                case SOUTH -> {
                    return bl ? DOOR_NORTH_SHAPE : (bl2 ? DOOR_WEST_SHAPE : DOOR_EAST_SHAPE);
                }
                case WEST -> {
                    return bl ? DOOR_EAST_SHAPE : (bl2 ? DOOR_NORTH_SHAPE : DOOR_SOUTH_SHAPE);
                }
                case NORTH -> {
                    return bl ? DOOR_SOUTH_SHAPE : (bl2 ? DOOR_EAST_SHAPE : DOOR_WEST_SHAPE);
                }
                default -> {
                    return bl ? DOOR_WEST_SHAPE : (bl2 ? DOOR_SOUTH_SHAPE : DOOR_NORTH_SHAPE);
                }
            }
        }

        if (state.is(Blocks.SCAFFOLDING)) {
            boolean above = player.boundingBox.minY > vector3i.getY() + 1 - 1.0E-5F;
            if (above && !player.getInputData().contains(PlayerAuthInputData.WANT_DOWN)) {
                return SCAFFOLDING_NORMAL_SHAPE;
            } else {
                return EMPTY_SHAPE;
            }
        }

        return null;
    }
}
