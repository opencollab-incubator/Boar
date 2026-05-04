package ac.boar.anticheat.util;

import ac.boar.anticheat.Boar;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class ReferencePopulator<T> {
    private final String key;
    private final Map<String, Reference.Deferred<T>> deferredReferences = new HashMap<>();

    private boolean populated = false;

    public ReferencePopulator(String key) {
        this.key = key;
    }

    public Reference.Deferred<T> defer(String key) {
        Reference.Deferred<T> value = new Reference.Deferred<>(key);
        this.deferredReferences.put(key, value);
        return value;
    }

    @Nullable
    public Reference<T> get(String key) {
        return this.deferredReferences.get(key);
    }

    public void populate(String key, T value) {
        Reference.Deferred<T> removed = this.deferredReferences.remove(key);
        if (removed == null) {
            Boar.debug("Attempted to populate a reference that was not registered: '" + this.key + "/" + key + "'", Boar.DebugMessage.WARNING);
            return;
        }

        removed.set(value);
    }

    public void populate(BiConsumer<String, Populator<T>> populator) {
        for (Map.Entry<String, Reference.Deferred<T>> entry : this.deferredReferences.entrySet()) {
            String key = entry.getKey();
            populator.accept(key, value -> {
                if (entry.getValue() != null) {
                    entry.getValue().set(value);
                } else {
                    Boar.debug("Attempted to populate a reference that was not registered: '" + this.key + "/" + key + "'", Boar.DebugMessage.WARNING);
                }
            });
        }

        this.deferredReferences.clear();
    }

    public void ensurePopulated(boolean requireAll) {
        if (this.populated) {
            throw new IllegalStateException("Cannot populate references more than once!");
        }

        if (!this.deferredReferences.isEmpty()) {
            if (requireAll) {
                throw new IllegalStateException("Some references were not populated for populator '" + this.key + "'");
            } else {
                Boar.debug("Some references were not populated for populator '" + this.key + "'", Boar.DebugMessage.WARNING);
            }
        }

        this.populated = true;
    }

    public interface Populator<T> {
        void populate(T value);
    }
}
