package ac.boar.geyser.mappings.block;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.util.Reference;
import ac.boar.anticheat.util.ReferencePopulator;
import ac.boar.geyser.mappings.item.GeyserItem;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.BlockMappings;
import ac.boar.mappings.item.Item;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.type.BedBlock;
import org.geysermc.geyser.level.block.type.ButtonBlock;
import org.geysermc.geyser.level.block.type.ChestBlock;
import org.geysermc.geyser.level.block.type.DoorBlock;
import org.geysermc.geyser.level.block.type.TrapDoorBlock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GeyserBlockMappings implements BlockMappings {
    private final Map<Item, Block> itemToBlock = new HashMap<>();
    private final List<Block> climbableBlocks = new ArrayList<>();
    private final List<Block> fenceBlocks = new ArrayList<>();
    private final List<Block> fenceGateBlocks = new ArrayList<>();
    private final List<Block> wallBlocks = new ArrayList<>();
    private final List<Block> shulkerBlocks = new ArrayList<>();
    private final List<Block> leavesBlocks = new ArrayList<>();
    private final List<Block> stairsBlocks = new ArrayList<>();
    private final List<Block> bedBlocks = new ArrayList<>();
    private final List<Block> chestBlocks = new ArrayList<>();
    private final List<Block> doorBlocks = new ArrayList<>();
    private final List<Block> trapDoorBlocks = new ArrayList<>();
    private final List<Block> candleBlocks = new ArrayList<>();
    private final List<Block> signBlocks = new ArrayList<>();
    private final List<Block> buttonBlocks = new ArrayList<>();

    private final Map<String, Block> keyToBlock = new HashMap<>();

    public static BlockMappings load() {
        GeyserBlockMappings mappings = new GeyserBlockMappings();

        for (Field field : Blocks.class.getDeclaredFields()) {
            try {
                Object object = field.get(null);

                if (object instanceof org.geysermc.geyser.level.block.type.Block block) {
                    final String lowercaseName = field.getName().toLowerCase(Locale.ROOT);
                    if (lowercaseName.endsWith("_fence")) {
                        mappings.fenceBlocks.add(new GeyserBlock(block));
                    } else if (lowercaseName.endsWith("_wall")) {
                        mappings.wallBlocks.add(new GeyserBlock(block));
                    } else if (lowercaseName.endsWith("_fence_gate")) {
                        mappings.fenceGateBlocks.add(new GeyserBlock(block));
                    } else if (lowercaseName.endsWith("shulker_box")) {
                        mappings.shulkerBlocks.add(new GeyserBlock(block));
                    } else if (lowercaseName.endsWith("_leaves")) {
                        mappings.leavesBlocks.add(new GeyserBlock(block));
                    } else if (lowercaseName.endsWith("_stairs")) {
                        mappings.stairsBlocks.add(new GeyserBlock(block));
                    } else if (block == Blocks.CANDLE || lowercaseName.endsWith("_candle")) {
                        mappings.candleBlocks.add(new GeyserBlock(block));
                    } else if (lowercaseName.endsWith("_sign")) {
                        mappings.signBlocks.add(new GeyserBlock(block));
                    }

                    if (block instanceof BedBlock) {
                        mappings.bedBlocks.add(new GeyserBlock(block));
                    }

                    if (block instanceof ChestBlock) {
                        mappings.chestBlocks.add(new GeyserBlock(block));
                    }

                    if (block instanceof DoorBlock) {
                        mappings.doorBlocks.add(new GeyserBlock(block));
                    }

                    if (block instanceof TrapDoorBlock) {
                        mappings.trapDoorBlocks.add(new GeyserBlock(block));
                    }

                    if (block instanceof ButtonBlock) {
                        mappings.buttonBlocks.add(new GeyserBlock(block));
                    }

                    org.geysermc.geyser.item.type.Item item = org.geysermc.geyser.item.type.Item.byBlock(block);
                    if (item.equals(Items.AIR)) {
                        continue;
                    }

                    mappings.itemToBlock.put(new GeyserItem(item), new GeyserBlock(block));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // Glow Berry
        mappings.climbableBlocks.add(new GeyserBlock(Blocks.CAVE_VINES));
        mappings.climbableBlocks.add(new GeyserBlock(Blocks.CAVE_VINES_PLANT));
        mappings.climbableBlocks.add(new GeyserBlock(Blocks.CAVE_VINES_PLANT));

        mappings.climbableBlocks.add(new GeyserBlock(Blocks.LADDER));
        mappings.climbableBlocks.add(new GeyserBlock(Blocks.VINE));

        // Nether stuff.
        mappings.climbableBlocks.add(new GeyserBlock(Blocks.TWISTING_VINES));
        mappings.climbableBlocks.add(new GeyserBlock(Blocks.TWISTING_VINES_PLANT));
        mappings.climbableBlocks.add(new GeyserBlock(Blocks.WEEPING_VINES));
        mappings.climbableBlocks.add(new GeyserBlock(Blocks.WEEPING_VINES_PLANT));
//        System.out.println("Cache: " + Arrays.toString(LEAVES_BLOCKS.toArray()));

        return mappings;
    }

    @Override
    public List<Block> getStairsBlocks() {
        return Collections.unmodifiableList(stairsBlocks);
    }

    @Override
    public List<Block> getLeavesBlocks() {
        return Collections.unmodifiableList(leavesBlocks);
    }

    @Override
    public List<Block> getShulkerBlocks() {
        return Collections.unmodifiableList(shulkerBlocks);
    }

    @Override
    public List<Block> getFenceGateBlocks() {
        return Collections.unmodifiableList(fenceGateBlocks);
    }

    @Override
    public List<Block> getWallBlocks() {
        return Collections.unmodifiableList(wallBlocks);
    }

    @Override
    public List<Block> getFenceBlocks() {
        return Collections.unmodifiableList(fenceBlocks);
    }

    @Override
    public List<Block> getClimbableBlocks() {
        return Collections.unmodifiableList(climbableBlocks);
    }

    @Override
    public List<Block> getBedBlocks() {
        return Collections.unmodifiableList(bedBlocks);
    }

    @Override
    public List<Block> getChestBlocks() {
        return Collections.unmodifiableList(chestBlocks);
    }

    @Override
    public List<Block> getDoorBlocks() {
        return Collections.unmodifiableList(doorBlocks);
    }

    @Override
    public List<Block> getTrapDoorBlocks() {
        return Collections.unmodifiableList(trapDoorBlocks);
    }

    @Override
    public List<Block> getCandleBlocks() {
        return Collections.unmodifiableList(candleBlocks);
    }

    @Override
    public List<Block> getSignBlocks() {
        return Collections.unmodifiableList(signBlocks);
    }

    @Override
    public List<Block> getButtonBlocks() {
        return Collections.unmodifiableList(buttonBlocks);
    }

    @Override
    public Map<Item, Block> getItemToBlock() {
        return Collections.unmodifiableMap(itemToBlock);
    }

    public static void finalizeBlockMappings(ReferencePopulator<Block> populator) {
        // Cannot use the block registry here as calling Block#javaIdentifier will throw a
        // NoSuchMethodException due to the Key class from Adventure being shaded into a different
        // package
        for (Field field : Blocks.class.getDeclaredFields()) {
            try {
                Object object = field.get(null);
                if (object instanceof org.geysermc.geyser.level.block.type.Block block) {
                    String key = "minecraft:" + field.getName().toLowerCase(Locale.ROOT);
                    Reference<Block> ref = populator.get(key);
                    if (ref != null) {
                        populator.populate(key, new GeyserBlock(block));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        populator.ensurePopulated(false);
    }
}
