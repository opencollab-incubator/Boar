package ac.boar.mappings.block;

import ac.boar.anticheat.util.Reference;
import ac.boar.anticheat.util.ReferencePopulator;

public final class Blocks {
    static final ReferencePopulator<Block> POPULATOR = new ReferencePopulator<>("block");

    public static final Reference<Block> AIR = create("air");
    public static final Reference<Block> ANVIL = create("anvil");
    public static final Reference<Block> BAMBOO = create("bamboo");
    public static final Reference<Block> BAMBOO_SAPLING = create("bamboo_sapling");
    public static final Reference<Block> BARREL = create("barrel");
    public static final Reference<Block> BEACON = create("beacon");
    public static final Reference<Block> BELL = create("bell");
    public static final Reference<Block> BLAST_FURNACE = create("blast_furnace");
    public static final Reference<Block> BLUE_ICE = create("blue_ice");
    public static final Reference<Block> BREWING_STAND = create("brewing_stand");
    public static final Reference<Block> BUBBLE_COLUMN = create("bubble_column");
    public static final Reference<Block> CACTUS = create("cactus");
    public static final Reference<Block> CAKE = create("cake");
    public static final Reference<Block> CAULDRON = create("cauldron");
    public static final Reference<Block> CAVE_AIR = create("cave_air");
    public static final Reference<Block> CHIPPED_ANVIL = create("chipped_anvil");
    public static final Reference<Block> CHISELED_BOOKSHELF = create("chiseled_bookshelf");
    public static final Reference<Block> COBWEB = create("cobweb");
    public static final Reference<Block> CONDUIT = create("conduit");
    public static final Reference<Block> DAMAGED_ANVIL = create("damaged_anvil");
    public static final Reference<Block> DRAGON_EGG = create("dragon_egg");
    public static final Reference<Block> END_PORTAL_FRAME = create("end_portal_frame");
    public static final Reference<Block> ENDER_CHEST =  create("ender_chest");
    public static final Reference<Block> FROSTED_ICE = create("frosted_ice");
    public static final Reference<Block> FURNACE = create("furnace");
    public static final Reference<Block> HONEY_BLOCK = create("honey_block");
    public static final Reference<Block> ICE = create("ice");
    public static final Reference<Block> LAVA = create("lava");
    public static final Reference<Block> LECTERN = create("lectern");
    public static final Reference<Block> LEVER = create("lever");
    public static final Reference<Block> NOTE_BLOCK = create("note_block");
    public static final Reference<Block> PACKED_ICE = create("packed_ice");
    public static final Reference<Block> POINTED_DRIPSTONE = create("pointed_dripstone");
    public static final Reference<Block> POWDER_SNOW = create("powder_snow");
    public static final Reference<Block> PUMPKIN = create("pumpkin");
    public static final Reference<Block> REDSTONE_ORE = create("redstone_ore");
    public static final Reference<Block> RESPAWN_ANCHOR = create("respawn_anchor");
    public static final Reference<Block> SCAFFOLDING = create("scaffolding");
    public static final Reference<Block> SEA_PICKLE = create("sea_pickle");
    public static final Reference<Block> SLIME_BLOCK = create("slime_block");
    public static final Reference<Block> SOUL_SAND = create("soul_sand");
    public static final Reference<Block> SWEET_BERRY_BUSH = create("sweet_berry_bush");
    public static final Reference<Block> TNT = create("tnt");
    public static final Reference<Block> TURTLE_EGG = create("turtle_egg");
    public static final Reference<Block> VAULT = create("vault");
    public static final Reference<Block> VOID_AIR = create("void_air");
    public static final Reference<Block> WATER = create("water");

    private static Reference<Block> create(String key) {
        return POPULATOR.defer("minecraft:" + key);
    }
}
