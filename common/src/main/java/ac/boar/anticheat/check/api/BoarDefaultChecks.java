package ac.boar.anticheat.check.api;

import ac.boar.anticheat.check.impl.badpackets.BadPacketA;
import ac.boar.anticheat.check.impl.badpackets.BadPacketB;
import ac.boar.anticheat.check.impl.prediction.DebugOffsetA;
import ac.boar.anticheat.check.impl.prediction.Prediction;
import ac.boar.anticheat.check.impl.reach.Reach;
import ac.boar.anticheat.check.impl.timer.Timer;

public final class BoarDefaultChecks {

    private BoarDefaultChecks() {
    }

    public static void registerAll(BoarCheckRegistry registry) {
        registry.register(Timer.class, Timer::new);

        registry.register(Reach.class, Reach::new);

        registry.register(DebugOffsetA.class, DebugOffsetA::new);
        registry.register(Prediction.class, Prediction::new);

        registry.register(BadPacketA.class, BadPacketA::new);
        registry.register(BadPacketB.class, BadPacketB::new);
    }
}
