package ac.boar.api.anticheat.model;

public interface NetworkSession extends MessageRecipient, Identifiable, Permissible {

    boolean isClosed();

    void disconnect(String reason);

    boolean requiresPingMagnitude();

    int protocolVersion();
}
