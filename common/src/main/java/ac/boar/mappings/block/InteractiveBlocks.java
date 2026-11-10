package ac.boar.mappings.block;

import java.util.List;
import java.util.Set;

/**
 * Blocks the Bedrock client treats as interactive when a player presses on them (ClientInputCallbacks::handleBuildAction): without sneaking, it uses the block instead of the held item.
 * The client checks BlockType::isInteractiveBlock, BlockType::isContainerBlock and Block::isCraftingBlock. This list holds the Java names of every vanilla class that overrides one of them to return true.
 * Subclasses are assumed to inherit the override (dropper from dispenser, smoker and blast furnace from furnace, trapped and ender chests and shulker boxes from chest); that was not checked in the client.
 */
public final class InteractiveBlocks {

    private static final Set<String> NAMES = Set.of(
            "anvil", "chipped_anvil", "damaged_anvil", "barrel", "beacon", "bell", "brewing_stand", "candle", "candle_cake", "cartography_table",
            "cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron", "chest", "trapped_chest", "ender_chest", "chiseled_bookshelf",
            "command_block", "chain_command_block", "repeating_command_block", "comparator", "composter", "crafter", "crafting_table", "daylight_detector",
            "decorated_pot", "dispenser", "dropper", "enchanting_table", "end_portal_frame", "flower_pot", "furnace", "blast_furnace", "smoker", "grindstone",
            "hopper", "jigsaw", "jukebox", "lectern", "lever", "loom", "note_block", "repeater", "respawn_anchor", "smithing_table", "stonecutter",
            "structure_block", "sweet_berry_bush", "vault", "shulker_box"
    );

    // Families named by suffix: beds, buttons, candles and candle cakes, copper chests and golem statues, doors, fence gates, potted plants (prefix), shelves, signs (all four kinds end in _sign), shulker boxes and trapdoors
    private static final List<String> SUFFIXES = List.of(
            "_bed", "_button", "_candle", "_candle_cake", "copper_chest", "copper_golem_statue", "_door", "_fence_gate",
            "_shelf", "_sign", "_shulker_box", "_trapdoor"
    );

    private InteractiveBlocks() {
    }

    // The Java block name without the namespace, for example "oak_door"
    public static boolean isInteractive(String name) {
        if (NAMES.contains(name) || name.startsWith("potted_")) {
            return true;
        }
        for (String suffix : SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
