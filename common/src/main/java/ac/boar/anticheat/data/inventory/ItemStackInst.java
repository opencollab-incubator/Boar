package ac.boar.anticheat.data.inventory;

import ac.boar.anticheat.BoarPlatform;

public final class ItemStackInst {
    private static ItemStackProvider provider;

    public static void init(BoarPlatform platform) {
        if (provider != null) {
            throw new IllegalStateException("ItemStackProvider has already been initialized");
        }

        provider = platform.itemStackProvider();
    }

    static ItemStackProvider provider() {
        return provider;
    }
}
