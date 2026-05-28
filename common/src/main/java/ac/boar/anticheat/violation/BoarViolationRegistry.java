package ac.boar.anticheat.violation;

import ac.boar.anticheat.Boar;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BoarViolationRegistry {
    private final List<ViolationListener> listeners = new CopyOnWriteArrayList<>();

    public void register(ViolationListener listener) {
        this.listeners.add(listener);
    }

    public void unregister(ViolationListener listener) {
        this.listeners.remove(listener);
    }

    public void clear() {
        this.listeners.clear();
    }

    public void dispatch(Violation violation) {
        for (final ViolationListener listener : this.listeners) {
            // A throwing listener must not abort the check that flagged, nor starve the other
            // listeners — each is independent.
            try {
                listener.onViolation(violation);
            } catch (Throwable t) {
                Boar.debug("Violation listener " + listener.getClass().getName() + " threw: " + t, Boar.DebugMessage.WARNING);
            }
        }
    }
}
