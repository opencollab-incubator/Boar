package ac.boar.anticheat.alert;

import ac.boar.anticheat.Boar;
import ac.boar.api.anticheat.model.ConsoleViewer;
import ac.boar.api.anticheat.model.Identifiable;
import ac.boar.api.anticheat.model.MessageRecipient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AlertManager {
    public final static UUID CONSOLE_UUID = new UUID(0, 0);

    private final Map<UUID, MessageRecipient> sources = new ConcurrentHashMap<>();

    public void alert(String verbose) {
        sources.values().forEach(source -> source.sendMessage(getPrefix() + "§3" + verbose));
    }

    public void alertToPlayers(final List<MessageRecipient> sources, String verbose) {
        sources.forEach(source -> source.sendMessage(getPrefix() + "§3" + verbose));
    }

    public String getPrefix() {
        return Boar.getConfig().formattedPrefix();
    }

    public boolean hasAlert(MessageRecipient source) {
        if (source instanceof ConsoleViewer) {
            return this.sources.containsKey(CONSOLE_UUID);
        } else if (source instanceof Identifiable identifiable) {
            return this.sources.containsKey(identifiable.uuid());
        }

        return false;
    }

    public void addAlert(MessageRecipient source) {
        if (source instanceof ConsoleViewer) {
            this.sources.put(CONSOLE_UUID, source);
        }  else if (source instanceof Identifiable identifiable) {
            this.sources.put(identifiable.uuid(), source);
        } else {
            Boar.debug("Could not add message recipient: " + source + " because it is not identifiable.", Boar.DebugMessage.WARNING);
        }
    }

    public void removeAlert(MessageRecipient source) {
        if (source instanceof ConsoleViewer) {
            this.sources.remove(CONSOLE_UUID);
        } else if (source instanceof Identifiable identifiable) {
            this.sources.remove(identifiable.uuid());
        } else {
            Boar.debug("Could not remove message recipient: " + source + " because it is not identifiable.", Boar.DebugMessage.WARNING);
        }
    }
}
