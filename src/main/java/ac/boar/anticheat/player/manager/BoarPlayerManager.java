package ac.boar.anticheat.player.manager;

import ac.boar.anticheat.player.BoarPlayer;

import ac.boar.geyser.util.GeyserUtil;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.session.GeyserSession;

import java.util.HashMap;

public class BoarPlayerManager extends HashMap<GeyserConnection, BoarPlayer> {
    public BoarPlayer add(GeyserConnection connection) {
        if (!(connection instanceof GeyserSession)) {
            return null;
        }

        final BoarPlayer player = new BoarPlayer((GeyserSession) connection);
        GeyserUtil.hook(player);
        this.put(connection, player);
        return player;
    }
}