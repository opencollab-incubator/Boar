package ac.boar.anticheat.data.enchantment;

import ac.boar.anticheat.BoarPlatform;

public final class EnchantmentInst {
    private static EnchantmentProvider provider;

    public static void init(BoarPlatform platform) {
        if (provider != null) {
            throw new IllegalStateException("Enchantment has already been initialized");
        }

        provider = platform.enchantmentProvider();
    }

    static EnchantmentProvider provider() {
        return provider;
    }
}
