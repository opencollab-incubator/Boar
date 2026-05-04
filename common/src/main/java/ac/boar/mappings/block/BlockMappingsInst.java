package ac.boar.mappings.block;

import ac.boar.anticheat.BoarPlatform;

public final class BlockMappingsInst {
    private static BlockMappings mappings;
    private static PropertyProvider propertyProvider;

    public static void init(BoarPlatform platform) {
        if (mappings != null) {
            throw new IllegalStateException("BlockMappings is already initialized");
        }

        mappings = platform.loadBlockMappings();
        propertyProvider = platform.propertyProvider();

        platform.finalizeBlockMappings(Blocks.POPULATOR);
    }

    static <T extends Comparable<T>> Property<T> property(String key) {
        return propertyProvider.get(key);
    }

    static BlockMappings get() {
        return mappings;
    }
}
