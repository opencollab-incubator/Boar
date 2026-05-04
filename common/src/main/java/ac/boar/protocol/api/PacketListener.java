package ac.boar.protocol.api;

public interface PacketListener {
    default void onPacketSend(final CloudburstPacketEvent event) {
    }

    default void onPacketReceived(final CloudburstPacketEvent event) {
    }
}