package ac.boar.anticheat.check.api.holder;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.check.api.CheckFactory;
import ac.boar.api.anticheat.annotations.CheckInfo;
import ac.boar.anticheat.player.BoarPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CheckHolder extends HashMap<Class<?>, Check> {
    public CheckHolder(final BoarPlayer player) {
        for (Map.Entry<Class<? extends Check>, CheckFactory> entry : Boar.getInstance().getCheckRegistry().factories().entrySet()) {
            this.put(entry.getKey(), entry.getValue().create(player));
        }
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
