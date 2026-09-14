package ac.boar.api.anticheat.model;

public interface NetworkSession extends MessageRecipient, Identifiable, Permissible {

    boolean isClosed();

    // closes the connection completely
    void disconnect(String reason);

    // can be used on implementations where Boar is running on a server connected to a proxy, and where we
    // don't want to fully disconnect the player. The primary use for this right now is doing server-kicks for NSL timeout
    default void disconnectFromServer(String reason) {
        disconnect(reason);
    }

    boolean requiresPingMagnitude();

    int protocolVersion();
}
