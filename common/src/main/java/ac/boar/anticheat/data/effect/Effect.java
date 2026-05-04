package ac.boar.anticheat.data.effect;

public enum Effect {
    JUMP_BOOST,
    LEVITATION,
    CONDUIT_POWER,
    SLOW_FALLING,
    BAD_OMEN,
    HERO_OF_THE_VILLAGE,
    DARKNESS,
    TRIAL_OMEN,
    WIND_CHARGED,
    WEAVING,
    OOZING,
    INFESTED,
    RAID_OMEN;

    public static Effect byId(int id) {
        return EffectInst.provider().byId(id);
    }
}
