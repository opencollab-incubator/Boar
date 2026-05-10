package ac.boar.anticheat.ack;

import ac.boar.anticheat.player.BoarPlayer;

import java.util.HashMap;
import java.util.Map;

public final class BoarAcknowledgmentRegistry {
    private final Map<Class<? extends Acknowledgment>, AcknowledgmentHandler<?>> handlers = new HashMap<>();

    public <A extends Acknowledgment> void register(Class<A> type, AcknowledgmentHandler<A> handler) {
        this.handlers.put(type, handler);
    }

    public boolean unregister(Class<? extends Acknowledgment> type) {
        return this.handlers.remove(type) != null;
    }

    @SuppressWarnings("unchecked")
    public <A extends Acknowledgment> AcknowledgmentHandler<A> handler(Class<A> type) {
        return (AcknowledgmentHandler<A>) this.handlers.get(type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void dispatch(BoarPlayer player, Acknowledgment ack) {
        AcknowledgmentHandler handler = this.handlers.get(ack.getClass());
        if (handler != null) {
            handler.handle(player, ack);
        }
    }
}
