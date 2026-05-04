package ac.boar.anticheat.check.api.holder;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.check.api.Check;
import ac.boar.api.anticheat.annotations.CheckInfo;
import ac.boar.anticheat.check.impl.badpackets.BadPacketA;
import ac.boar.anticheat.check.impl.badpackets.BadPacketB;
import ac.boar.anticheat.check.impl.prediction.DebugOffsetA;
import ac.boar.anticheat.check.impl.prediction.Prediction;
import ac.boar.anticheat.check.impl.reach.Reach;
import ac.boar.anticheat.check.impl.timer.Timer;
import ac.boar.anticheat.player.BoarPlayer;

import java.util.HashMap;
import java.util.List;

public class CheckHolder extends HashMap<Class<?>, Check> {
    public CheckHolder(final BoarPlayer player) {
        this.put(Timer.class, new Timer(player));

        this.put(Reach.class, new Reach(player));

        this.put(DebugOffsetA.class, new DebugOffsetA(player));
        this.put(Prediction.class, new Prediction(player));

        this.put(BadPacketA.class, new BadPacketA(player));
        this.put(BadPacketB.class, new BadPacketB(player));
    }

    public void manuallyFail(Class<?> klass) {
        this.manuallyFail(klass, "");
    }

    public void manuallyFail(Class<?> klass, String verbose) {
        Check check = this.get(klass);
        if (check != null) {
            check.fail(verbose);
        }
    }

    @Override
    public Check put(Class<?> key, Check value) {
        String name = key.getDeclaredAnnotation(CheckInfo.class).name(), type = key.getDeclaredAnnotation(CheckInfo.class).type();
        List<String> disabledChecks = Boar.getConfig().disabledChecks();
        if (type.isEmpty() ? disabledChecks.contains(name) : disabledChecks.contains(name + "-" + type)) {
            return null;
        }

        return super.put(key, value);
    }
}
