package ac.boar.geyser.anticheat.data.effect;

import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.data.effect.EffectProvider;
import org.geysermc.geyser.level.EffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GeyserEffectProvider implements EffectProvider {
    private static final GeyserEffectProvider INSTANCE = new GeyserEffectProvider();

    private static final EffectType[] TABLE;

    static {
        List<EffectType> values = new ArrayList<>(Arrays.asList(EffectType.values()));
        values.removeIf(type -> type.getBedrockId() == 0 && type != EffectType.NONE);
        TABLE = new EffectType[values.size()];

        for (EffectType value : values) {
            TABLE[value.getBedrockId()] = value;
        }
    }

    @Override
    public Effect byId(int id) {
        // Sanity check
        if (id < 0 || id >= TABLE.length) {
            return null;
        }

        return Effect.valueOf(TABLE[id].getJavaEffect().name());
    }

    public static EffectProvider get() {
        return INSTANCE;
    }
}
