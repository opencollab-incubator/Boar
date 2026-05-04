package ac.boar.anticheat;

import ac.boar.anticheat.alert.AlertManager;
import ac.boar.anticheat.config.Config;
import ac.boar.anticheat.config.ConfigLoader;
import ac.boar.anticheat.data.block.BoarBlockStateInst;
import ac.boar.anticheat.data.effect.EffectInst;
import ac.boar.anticheat.data.enchantment.EnchantmentInst;
import ac.boar.anticheat.data.inventory.ItemStackInst;
import ac.boar.anticheat.packets.input.AuthInputPackets;
import ac.boar.anticheat.packets.input.PostAuthInputPackets;
import ac.boar.anticheat.packets.other.NetworkLatencyPackets;
import ac.boar.anticheat.packets.other.PacketCheckRunner;
import ac.boar.anticheat.packets.other.VehiclePackets;
import ac.boar.anticheat.packets.player.PlayerEffectPackets;
import ac.boar.anticheat.packets.player.PlayerInventoryPackets;
import ac.boar.anticheat.packets.player.PlayerVelocityPackets;
import ac.boar.anticheat.packets.server.ServerChunkPackets;
import ac.boar.anticheat.packets.server.ServerDataPackets;
import ac.boar.anticheat.packets.server.ServerEntityPackets;
import ac.boar.anticheat.player.BoarPlayerManager;
import ac.boar.mappings.block.BlockMappingsInst;
import ac.boar.mappings.entity.EntityMappingsInst;
import ac.boar.mappings.item.ItemMappingsInst;
import ac.boar.protocol.PacketEvents;
import lombok.Getter;
import lombok.Setter;

@Getter
public class Boar {
    @Getter
    private final static Boar instance = new Boar();
    @Getter @Setter
    private static Config config;
    private Boar() {}

    private BoarPlayerManager<?> playerManager;
    private AlertManager alertManager;

    private BoarPlatform platform;

    public void init(BoarPlatform platform) {
        this.platform = platform;

        config = ConfigLoader.load(platform, platform.getClass(), Config.class, Config.DEFAULT_CONFIG);

        BlockMappingsInst.init(platform);
        EntityMappingsInst.init(platform);
        ItemMappingsInst.init(platform);
        ItemStackInst.init(platform);

        BoarBlockStateInst.init(platform);
        EffectInst.init(platform);
        EnchantmentInst.init(platform);

        this.playerManager = platform.playerManager();
        this.alertManager = new AlertManager();

        PacketEvents.getApi().register(new NetworkLatencyPackets());
        PacketEvents.getApi().register(new ServerChunkPackets());
        PacketEvents.getApi().register(new ServerEntityPackets());
        PacketEvents.getApi().register(new ServerDataPackets());
        PacketEvents.getApi().register(new PlayerEffectPackets());
        PacketEvents.getApi().register(new PlayerVelocityPackets());
        PacketEvents.getApi().register(new PlayerInventoryPackets());
        PacketEvents.getApi().register(new VehiclePackets());
        PacketEvents.getApi().register(new PacketCheckRunner());
        PacketEvents.getApi().register(new AuthInputPackets());
        PacketEvents.getApi().register(new PostAuthInputPackets());
    }

    public void terminate(BoarPlatform platform) {
        PacketEvents.getApi().terminate();
        this.playerManager.clear();

        ConfigLoader.save(this.platform, platform.getClass(), config);
    }

    public static void debug(String message, DebugMessage type) {
        if (!config.debugMode()) {
            return;
        }

        switch (type) {
            case INFO -> instance.platform.logger().info(message);
            case WARNING -> instance.platform.logger().warn(message);
            case SERVE -> instance.platform.logger().error(message);
        }
    }

    public enum DebugMessage {
        INFO, WARNING, SERVE;
    }
}
