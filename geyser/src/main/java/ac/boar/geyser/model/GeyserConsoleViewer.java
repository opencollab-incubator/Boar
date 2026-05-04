package ac.boar.geyser.model;

import ac.boar.api.anticheat.model.ConsoleViewer;
import org.geysermc.geyser.api.command.CommandSource;

import java.util.Objects;

public record GeyserConsoleViewer(CommandSource source) implements ConsoleViewer {

    public GeyserConsoleViewer(CommandSource source) {
        if (!source.isConsole()) {
            throw new IllegalArgumentException("CommandSource must be a console source");
        }

        this.source = source;
    }

    @Override
    public void sendMessage(String message) {
        this.source.sendMessage(message);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GeyserConsoleViewer that = (GeyserConsoleViewer) o;
        return Objects.equals(this.source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.source);
    }
}
