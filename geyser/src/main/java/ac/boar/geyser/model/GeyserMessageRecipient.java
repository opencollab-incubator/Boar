package ac.boar.geyser.model;

import ac.boar.anticheat.alert.AlertManager;
import ac.boar.api.anticheat.model.Identifiable;
import ac.boar.api.anticheat.model.MessageRecipient;
import org.geysermc.geyser.api.command.CommandSource;

import java.util.Objects;
import java.util.UUID;

public record GeyserMessageRecipient(CommandSource source) implements MessageRecipient, Identifiable {

    @Override
    public void sendMessage(String message) {
        this.source.sendMessage(message);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GeyserMessageRecipient recipient = (GeyserMessageRecipient) o;
        return Objects.equals(this.source, recipient.source);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.source);
    }

    @Override
    public String name() {
        return this.source.name();
    }

    @Override
    public UUID uuid() {
        return this.source.playerUuid() != null ? this.source.playerUuid() : AlertManager.CONSOLE_UUID;
    }
}
