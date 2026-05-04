package ac.boar.anticheat.data.effect;

import ac.boar.anticheat.BoarPlatform;

public final class EffectInst {
    private static EffectProvider provider;

    public static void init(BoarPlatform platform) {
        if (provider != null) {
            throw new IllegalStateException("Effect has already been initialized");
        }

        provider = platform.effectProvider();
    }

    static EffectProvider provider() {
        return provider;
    }
}
