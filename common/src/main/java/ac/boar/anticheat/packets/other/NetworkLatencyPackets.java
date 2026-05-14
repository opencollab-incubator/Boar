package ac.boar.anticheat.packets.other;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;

/**
 * Netty-bridge listener: observes inbound and outbound NetworkStackLatency packets on the wire
 * and forwards the events to the player's {@link ac.boar.anticheat.ack.BoarAcknowledgmentTransport}.
 * The transport owns all dispatch / kick / cancel decisions.
 */
public class NetworkLatencyPackets implements PacketListener {
    public final static long LATENCY_MAGNITUDE = 1000000L;
    public final static long PS5_LATENCY_MAGNITUDE = 1000L;

    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        if (!(event.getPacket() instanceof NetworkStackLatencyPacket packet)) {
            return;
        }

        event.getPlayer().getAckTransport().onPingSent(packet.getTimestamp(), !packet.isFromServer());
        packet.setFromServer(true);
    }

    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        if (!(event.getPacket() instanceof NetworkStackLatencyPacket packet)) {
            return;
        }

        final BoarPlayer player = event.getPlayer();
        // Bedrock player have different latency magnitude depending on the platform. We only
        // know about PS5 currently — see BedrockPlatform.PS4 (misleading name).
        long id = packet.getTimestamp() / (player.getSession().requiresPingMagnitude() ? PS5_LATENCY_MAGNITUDE : LATENCY_MAGNITUDE);
        if (player.getAckTransport().onPingReceived(id)) {
            event.setCancelled(true);
        }
    }
}
