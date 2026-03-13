package ac.boar.geyser;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.alert.AlertManager;
import ac.boar.anticheat.config.Config;
import ac.boar.anticheat.config.ConfigLoader;
import ac.boar.anticheat.player.BoarPlayer;
import lombok.Getter;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.event.lifecycle.*;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionLogger;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.session.GeyserSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GeyserBoar implements Extension {
    @Getter
    private static ExtensionLogger logger;
    private static final Map<String, GeyserSession> nameToSessions = new HashMap<>();

    @Subscribe
    public void onSessionJoin(SessionLoginEvent event) {
        BoarPlayer player = Boar.getInstance().getPlayerManager().add(event.connection());
        if (player == null) {
            return;
        }

        nameToSessions.put(event.connection().bedrockUsername(), (GeyserSession) event.connection());
    }

    @Subscribe
    public void onSessionLeave(SessionDisconnectEvent event) {
        BoarPlayer player = Boar.getInstance().getPlayerManager().remove(event.connection());
        if (player == null) {
            return;
        }

        nameToSessions.remove(event.connection().bedrockUsername());
    }

    @Subscribe
    public void onGeyserPostInitializeEvent(GeyserPostInitializeEvent event) {
        logger = this.logger();

        Boar.getInstance().init(this);
    }

    @Subscribe
    public void onGeyserShutdown(GeyserShutdownEvent event) {
        Boar.getInstance().terminate(this);
    }

    @Subscribe
    public void onRegisterPermissions(GeyserRegisterPermissionsEvent event) {
        event.register("boar.exempt", TriState.FALSE);
        event.register("boar.alert", TriState.NOT_SET);
        event.register("boar.debug", TriState.NOT_SET);
        event.register("boar.reload", TriState.NOT_SET);
    }

    @Subscribe
    public void onDefineCommands(GeyserDefineCommandsEvent event) {
        event.register(Command.builder(this).source(CommandSource.class)
                .name("reload")
                .description("Reload the config for Boar.")
                .permission("boar.reload")
                .executor((source, cmd, args) -> {
                    Boar.setConfig(ConfigLoader.load(this, GeyserBoar.class, Config.class, Config.DEFAULT_CONFIG));

                    final String prefix = Boar.getInstance().getAlertManager().getPrefix(source);
                    source.sendMessage(prefix + "§fReloaded config! New config: " + Boar.getConfig());
                })
                .build());

        event.register(Command.builder(this).source(CommandSource.class)
                .name("alert")
                .description("Enable alert messages.")
                .permission("boar.alert")
                .executor((source, cmd, args) -> {
                    AlertManager alertManager = Boar.getInstance().getAlertManager();

                    String prefix = alertManager.getPrefix(source);
                    if (alertManager.hasAlert(source)) {
                        alertManager.removeAlert(source);
                        source.sendMessage(prefix + "§fDisabled alerts.");
                    } else {
                        alertManager.addAlert(source);
                        source.sendMessage(prefix + "§fEnabled alerts.");
                    }
                })
                .build());

        event.register(Command.builder(this).source(CommandSource.class)
                .name("debug")
                .description("Enable prediction debug message.")
                .permission("boar.debug")
                .executor((source, cmd, args) -> {
                    if (args.length < 1) {
                        return;
                    }

                    final String prefix = Boar.getInstance().getAlertManager().getPrefix(source);
                    GeyserSession session = nameToSessions.get(args[0]);
                    BoarPlayer player = Boar.getInstance().getPlayerManager().get(session);
                    if (session == null || player == null) {
                        source.sendMessage(prefix + "§cFailed to find player session.");
                        return;
                    }

                    UUID uuid = source.isConsole() ? AlertManager.CONSOLE_UUID : source.playerUuid();
                    if (player.getTrackedDebugPlayers().containsKey(uuid)) {
                        player.getTrackedDebugPlayers().remove(uuid);
                    } else {
                        player.getTrackedDebugPlayers().put(uuid, source);
                    }
                })
                .build());
    }
}
