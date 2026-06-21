package ac.boar.anticheat.alert;

import ac.boar.anticheat.Boar;
import org.geysermc.geyser.api.command.CommandSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AlertManager {
    public final static UUID CONSOLE_UUID = new UUID(0, 0);

    private final Map<UUID, CommandSource> sources = new ConcurrentHashMap<>();

    public void alert(String verbose) {
        sources.values().forEach(source -> source.sendMessage(getPrefix(source) + "§3" + verbose));
    }

    public void alertToPlayers(final List<CommandSource> sources, String verbose) {
        sources.forEach(source -> source.sendMessage(getPrefix(source) + "§3" + verbose));
    }

    public String getPrefix(CommandSource source) {
        if (source.connection() != null) {
            return Boar.getConfig().bedrockPrefix();
        }

        return Boar.getConfig().prefix();
    }

    public boolean hasAlert(CommandSource source) {
        return this.sources.containsKey(source.isConsole() ? CONSOLE_UUID : source.playerUuid());
    }

    public void addAlert(CommandSource source) {
        this.sources.put(source.isConsole() ? CONSOLE_UUID : source.playerUuid(), source);
    }

    public void removeAlert(CommandSource source) {
        this.sources.remove(source.isConsole() ? CONSOLE_UUID : source.playerUuid());
    }
}
