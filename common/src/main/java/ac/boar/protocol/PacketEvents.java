package ac.boar.protocol;

import ac.boar.protocol.api.PacketListener;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class PacketEvents {
    @Getter
    private static final PacketEvents api = new PacketEvents();
    private PacketEvents() {}

    private final List<PacketListener> listeners = new ArrayList<>();

    public void register(final PacketListener... listener) {
        this.listeners.addAll(List.of(listener));
    }

    public void terminate() {
        this.listeners.clear();
    }
}
