package ac.boar.protocol;

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

public final class BoarDefaultPacketListeners {

    private BoarDefaultPacketListeners() {
    }

    public static void registerAll() {
        PacketEvents api = PacketEvents.getApi();
        api.register(new NetworkLatencyPackets());
        api.register(new ServerChunkPackets());
        api.register(new ServerEntityPackets());
        api.register(new ServerDataPackets());
        api.register(new PlayerEffectPackets());
        api.register(new PlayerVelocityPackets());
        api.register(new PlayerInventoryPackets());
        api.register(new VehiclePackets());
        api.register(new PacketCheckRunner());
        api.register(new AuthInputPackets());
        api.register(new PostAuthInputPackets());
    }
}
