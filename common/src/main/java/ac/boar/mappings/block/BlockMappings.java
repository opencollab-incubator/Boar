package ac.boar.mappings.block;

import ac.boar.mappings.item.Item;

import java.util.List;
import java.util.Map;

public interface BlockMappings {

    List<Block> getStairsBlocks();

    List<Block> getLeavesBlocks();

    List<Block> getShulkerBlocks();

    List<Block> getFenceGateBlocks();

    List<Block> getWallBlocks();

    List<Block> getFenceBlocks();

    List<Block> getClimbableBlocks();

    List<Block> getBedBlocks();

    List<Block> getChestBlocks();

    List<Block> getDoorBlocks();

    List<Block> getTrapDoorBlocks();

    List<Block> getCandleBlocks();

    List<Block> getSignBlocks();

    List<Block> getButtonBlocks();

    List<Block> getBarsBlocks();

    List<Block> getLanternBlocks();

    List<Block> getAnvilBlocks();

    List<Block> getCauldronBlocks();

    Map<Item, Block> getItemToBlock();

    static BlockMappings get() {
        return BlockMappingsInst.get();
    }
}
