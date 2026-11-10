package ac.boar.anticheat.check.api;

import ac.boar.anticheat.check.impl.badpackets.BadPacketA;
import ac.boar.anticheat.check.impl.badpackets.BadPacketB;
import ac.boar.anticheat.check.impl.inventory.Inventory;
import ac.boar.anticheat.check.impl.prediction.DebugOffsetA;
import ac.boar.anticheat.check.impl.prediction.Prediction;
import ac.boar.anticheat.check.impl.reach.Reach;
import ac.boar.anticheat.check.impl.timer.Timer;
import ac.boar.anticheat.check.impl.velocity.Velocity;
import ac.boar.anticheat.validator.blockbreak.ServerBreakBlockValidator;

public final class BoarDefaultChecks {

    private BoarDefaultChecks() {
    }

    public static void registerAll(BoarCheckRegistry registry) {
        registry.register(Timer.class, Timer::new);

        registry.register(Reach.class, Reach::new);
        registry.register(Inventory.class, Inventory::new);
        registry.register(ServerBreakBlockValidator.class, ServerBreakBlockValidator::new);

        registry.register(DebugOffsetA.class, DebugOffsetA::new);
        registry.register(Prediction.class, Prediction::new);
        registry.register(Velocity.class, Velocity::new);

        registry.register(BadPacketA.class, BadPacketA::new);
        registry.register(BadPacketB.class, BadPacketB::new);
    }
}
