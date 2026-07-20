package ac.boar.anticheat.data.effect;

public interface EffectProvider {

    // Returns the Boar effect represented by the platform effect ID, or null when the effect is not used by Boar
    Effect byId(int id);
}
