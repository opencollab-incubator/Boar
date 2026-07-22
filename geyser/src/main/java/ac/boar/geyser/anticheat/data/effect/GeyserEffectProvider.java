package ac.boar.geyser.anticheat.data.effect;

import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.data.effect.EffectProvider;
import org.geysermc.geyser.level.EffectType;

import java.util.Arrays;

public class GeyserEffectProvider implements EffectProvider {
    private static final GeyserEffectProvider INSTANCE = new GeyserEffectProvider();

    private static final Effect[] EFFECTS_BY_BEDROCK_ID;

    static {
        int maxBedrockId = Arrays.stream(EffectType.values())
                .mapToInt(EffectType::getBedrockId)
                .max()
                .orElse(0);
        EFFECTS_BY_BEDROCK_ID = new Effect[maxBedrockId + 1];

        for (EffectType type : EffectType.values()) {
            Effect effect = switch (type) {
                case SPEED -> Effect.SPEED;
                case SLOWNESS -> Effect.SLOWNESS;
                case JUMP_BOOST -> Effect.JUMP_BOOST;
                case LEVITATION -> Effect.LEVITATION;
                case CONDUIT_POWER -> Effect.CONDUIT_POWER;
                case SLOW_FALLING -> Effect.SLOW_FALLING;
                case BAD_OMEN -> Effect.BAD_OMEN;
                case HERO_OF_THE_VILLAGE -> Effect.HERO_OF_THE_VILLAGE;
                case DARKNESS -> Effect.DARKNESS;
                case TRIAL_OMEN -> Effect.TRIAL_OMEN;
                case WIND_CHARGED -> Effect.WIND_CHARGED;
                case WEAVING -> Effect.WEAVING;
                case OOZING -> Effect.OOZING;
                case INFESTED -> Effect.INFESTED;
                case RAID_OMEN -> Effect.RAID_OMEN;
                default -> null;
            };

            if (effect != null) {
                EFFECTS_BY_BEDROCK_ID[type.getBedrockId()] = effect;
            }
        }
    }

    @Override
    public Effect byId(int id) {
        if (id < 0 || id >= EFFECTS_BY_BEDROCK_ID.length) {
            return null;
        }

        return EFFECTS_BY_BEDROCK_ID[id];
    }

    public static EffectProvider get() {
        return INSTANCE;
    }
}
