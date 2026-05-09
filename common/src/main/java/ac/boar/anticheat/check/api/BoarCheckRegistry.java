package ac.boar.anticheat.check.api;

import ac.boar.api.anticheat.annotations.CheckInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BoarCheckRegistry {
    private final Map<Class<? extends Check>, CheckFactory> factories = new LinkedHashMap<>();

    public <T extends Check> void register(Class<T> checkClass, CheckFactory factory) {
        if (checkClass.getDeclaredAnnotation(CheckInfo.class) == null) {
            throw new IllegalArgumentException("Check class " + checkClass.getName() + " must be annotated with @CheckInfo");
        }
        this.factories.put(checkClass, factory);
    }

    public boolean unregister(Class<? extends Check> checkClass) {
        return this.factories.remove(checkClass) != null;
    }

    public void clear() {
        this.factories.clear();
    }

    public Map<Class<? extends Check>, CheckFactory> factories() {
        return Collections.unmodifiableMap(this.factories);
    }
}
