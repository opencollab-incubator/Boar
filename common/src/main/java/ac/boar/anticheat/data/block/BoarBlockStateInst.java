package ac.boar.anticheat.data.block;

import ac.boar.anticheat.BoarPlatform;

public final class BoarBlockStateInst {
    private static BlockStateFactory factory;

    public static void init(BoarPlatform platform) {
        if (factory != null) {
            throw  new IllegalStateException("BoarBlockState has already been initialized");
        }

        factory = platform.blockStateFactory();
    }

    static BlockStateFactory factory() {
        return factory;
    }
}
