package ac.boar.anticheat.data.vanilla;

import ac.boar.anticheat.data.effect.Effect;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class StatusEffect {
    private final Effect effect;
    private final int amplifier;
    private int duration;

    public void tick() {
        if (duration > 0) {
            duration--;
        }
    }
}
