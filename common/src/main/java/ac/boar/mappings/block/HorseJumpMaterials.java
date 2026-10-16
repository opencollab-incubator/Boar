package ac.boar.mappings.block;

/** Maps block names to horse jump materials. Used with the raw block IDs the player's palette really defines. */
public final class HorseJumpMaterials {
    private HorseJumpMaterials() {}

    /** These name rules were measured. We use them for every client version. */
    public static HorseJumpMaterial resolve(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return HorseJumpMaterial.UNKNOWN;
        }
        return switch (identifier) {
            case "minecraft:honey_block" -> HorseJumpMaterial.HONEY;
            case "minecraft:black_carpet", "minecraft:blue_carpet", "minecraft:brown_carpet", "minecraft:cyan_carpet", "minecraft:gray_carpet", "minecraft:green_carpet", "minecraft:light_blue_carpet", "minecraft:light_gray_carpet",
                    "minecraft:lime_carpet", "minecraft:magenta_carpet", "minecraft:orange_carpet", "minecraft:pink_carpet", "minecraft:purple_carpet", "minecraft:red_carpet", "minecraft:white_carpet", "minecraft:yellow_carpet",
                    "minecraft:moss_carpet", "minecraft:pale_moss_carpet" -> HorseJumpMaterial.CARPET;
            // Older palettes use this one name for all colors. We did not measure it.
            case "minecraft:carpet" -> HorseJumpMaterial.UNKNOWN;
            // Custom blocks may change horse jumping. We do not handle that.
            default -> HorseJumpMaterial.NONE;
        };
    }
}
