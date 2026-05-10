package ac.boar.geyser;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.ack.BoarDefaultAcknowledgments;
import ac.boar.anticheat.alert.AlertManager;
import ac.boar.anticheat.check.api.BoarDefaultChecks;
import ac.boar.anticheat.config.Config;
import ac.boar.anticheat.config.ConfigLoader;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.protocol.BoarDefaultPacketListeners;
import ac.boar.geyser.model.GeyserMessageRecipient;
import ac.boar.geyser.player.GeyserPlayerManager;
import lombok.Getter;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCommandsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserRegisterPermissionsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
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

    private GeyserBoarPlatform platform;

    @Subscribe
    public void onSessionJoin(SessionLoginEvent event) {
        GeyserConnection connection = event.connection();
        if (!(connection instanceof GeyserSession session)) {
            return;
        }

        BoarPlayer player = this.platform.playerManager().add(session);
        if (player == null) {
            return;
        }

        System.out.println("Adding name " + event.connection().bedrockUsername() + " " + session);
        nameToSessions.put(event.connection().bedrockUsername(), session);
    }

    @Subscribe
    public void onSessionLeave(SessionDisconnectEvent event) {
        BoarPlayer player = Boar.getInstance().getPlayerManager().remove(event.connection());
        if (player == null) {
            return;
        }

        if (player.future != null) {
            player.future.cancel(false);
        }

        nameToSessions.remove(event.connection().bedrockUsername());
    }

    @Subscribe
    public void onGeyserPostInitializeEvent(GeyserPostInitializeEvent event) {
        logger = this.logger();

        this.platform = new GeyserBoarPlatform(
                this.dataFolder(),
                new GeyserBoarLogger(this.logger()),
                new GeyserPlayerManager()
        );

        Boar.getInstance().init(this.platform);
        BoarDefaultChecks.registerAll(Boar.getInstance().getCheckRegistry());
        BoarDefaultAcknowledgments.registerAll(Boar.getInstance().getAcknowledgmentRegistry());
        BoarDefaultPacketListeners.registerAll();
    }

    @Subscribe
    public void onGeyserShutdown(GeyserShutdownEvent event) {
        Boar.getInstance().terminate(this.platform);
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
                    Boar.setConfig(ConfigLoader.load(this.platform, this.platform.getClass(), Config.class, Config.DEFAULT_CONFIG));

                    final String prefix = Boar.getInstance().getAlertManager().getPrefix(new GeyserMessageRecipient(source));
                    source.sendMessage(prefix + "§fReloaded config! New config: " + Boar.getConfig());
                })
                .build());

        event.register(Command.builder(this).source(CommandSource.class)
                .name("alert")
                .description("Enable alert messages.")
                .permission("boar.alert")
                .executor((source, cmd, args) -> {
                    AlertManager alertManager = Boar.getInstance().getAlertManager();

                    GeyserMessageRecipient recipient = new GeyserMessageRecipient(source);
                    String prefix = alertManager.getPrefix(recipient);
                    if (alertManager.hasAlert(recipient)) {
                        alertManager.removeAlert(recipient);
                        source.sendMessage(prefix + "§fDisabled alerts.");
                    } else {
                        alertManager.addAlert(recipient);
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

                    final String prefix = Boar.getInstance().getAlertManager().getPrefix(new GeyserMessageRecipient(source));
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
                        player.getTrackedDebugPlayers().put(uuid, new GeyserMessageRecipient(source));
                    }
                })
                .build());
    }
}
