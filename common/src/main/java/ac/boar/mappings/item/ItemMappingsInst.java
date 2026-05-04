package ac.boar.mappings.item;

import ac.boar.anticheat.BoarPlatform;

public final class ItemMappingsInst {
    private static ItemMappings mappings;

    public static void init(BoarPlatform platform) {
        if (mappings != null) {
            throw new IllegalStateException("ItemMappings is already initialized");
        }

        mappings = platform.loadItemMappings();

        platform.finalizeItemMappings(Items.POPULATOR);
    }

    static ItemMappings get() {
        return mappings;
    }
}
