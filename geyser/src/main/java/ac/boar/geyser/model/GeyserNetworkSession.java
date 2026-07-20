package ac.boar.geyser.model;

import ac.boar.api.anticheat.model.NetworkSession;
import org.geysermc.api.util.BedrockPlatform;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Objects;
import java.util.UUID;

public record GeyserNetworkSession(GeyserSession session) implements NetworkSession {

    @Override
    public String name() {
        return this.session.name();
    }

    @Override
    public UUID uuid() {
        return this.session.playerUuid();
    }

    @Override
    public void sendMessage(String message) {
        this.session.sendMessage(message);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GeyserNetworkSession that = (GeyserNetworkSession) o;
        return Objects.equals(this.session, that.session);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.session);
    }

    @Override
    public boolean isClosed() {
        return this.session.isClosed() || this.session.getUpstream().isClosed();
    }

    @Override
    public void disconnect(String reason) {
        this.session.disconnect(reason);
    }

    @Override
    public boolean requiresPingMagnitude() {
        return this.session.platform() == BedrockPlatform.PS4;
    }

    @Override
    public int protocolVersion() {
        return this.session.protocolVersion();
    }

    @Override
    public boolean hasPermission(String permission) {
        return this.session.hasPermission(permission);
    }
}
