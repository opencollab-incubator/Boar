package ac.boar.mappings.item;

import ac.boar.anticheat.util.Reference;
import ac.boar.anticheat.util.ReferencePopulator;

public final class Items {
    static final ReferencePopulator<Item> POPULATOR = new ReferencePopulator<>("item");

    public static final Reference<Item> BONE_MEAL = create("bone_meal");
    public static final Reference<Item> BOW = create("bow");
    public static final Reference<Item> BUCKET = create("bucket");
    public static final Reference<Item> CROSSBOW = create("crossbow");
    public static final Reference<Item> DIAMOND_SPEAR = create("diamond_spear");
    public static final Reference<Item> ELYTRA = create("elytra");
    public static final Reference<Item> ENDER_EYE = create("ender_eye");
    public static final Reference<Item> FIRE_CHARGE = create("fire_charge");
    public static final Reference<Item> FIREWORK_ROCKET = create("firework_rocket");
    public static final Reference<Item> FLINT_AND_STEEL = create("flint_and_steel");
    public static final Reference<Item> GLOWSTONE = create("glowstone");
    public static final Reference<Item> GOLDEN_SPEAR = create("golden_spear");
    public static final Reference<Item> IRON_SPEAR = create("iron_spear");
    public static final Reference<Item> LAVA_BUCKET = create("lava_bucket");
    public static final Reference<Item> LEATHER_BOOTS = create("leather_boots");
    public static final Reference<Item> NETHERITE_SPEAR = create("netherite_spear");
    public static final Reference<Item> OMINOUS_BOTTLE = create("ominous_bottle");
    public static final Reference<Item> POTION  = create("potion");
    public static final Reference<Item> POWDER_SNOW_BUCKET = create("powder_snow_bucket");
    public static final Reference<Item> SCAFFOLDING = create("scaffolding");
    public static final Reference<Item> SPYGLASS = create("spyglass");
    public static final Reference<Item> STONE_SPEAR = create("stone_spear");
    public static final Reference<Item> TRIDENT = create("trident");
    public static final Reference<Item> WATER_BUCKET = create("water_bucket");
    public static final Reference<Item> WOODEN_SPEAR = create("wooden_spear");

    private static Reference<Item> create(String key) {
        return POPULATOR.defer("minecraft:" + key);
    }
}
