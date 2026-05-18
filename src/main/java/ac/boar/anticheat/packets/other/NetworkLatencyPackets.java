package ac.boar.anticheat.packets.other;

import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.LatencyUtil;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.geysermc.api.util.BedrockPlatform;

public class NetworkLatencyPackets implements PacketListener {
    public final static long LATENCY_MAGNITUDE = 1000000L;
    public final static long PS5_LATENCY_MAGNITUDE = 1000L;

    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        if (!(event.getPacket() instanceof NetworkStackLatencyPacket packet)) {
            return;
        }

        if (!packet.isFromServer()) {
            packet.setFromServer(true);
            return;
        }

        event.getPlayer().getLatencyUtil().queue(packet.getTimestamp(), false);
    }

    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        if (!(event.getPacket() instanceof NetworkStackLatencyPacket packet)) {
            return;
        }
        final BoarPlayer player = event.getPlayer();
        final LatencyUtil.Latency poll = player.getLatencyUtil().sentQueue().poll();

        // Bedrock player have different latency magnitude depending on the platform, however this is the only one we know about
        // TODO: Figure out what the magnitude for PS4 is, currently we only know PS5 (BedrockPlatform.PS4 is an misleading name.)
        long id = packet.getTimestamp() / (player.getSession().platform() == BedrockPlatform.PS4 ? PS5_LATENCY_MAGNITUDE : LATENCY_MAGNITUDE);

        if (poll == null || poll.id() != id) {
            // TODO: Fix this... lol seems to be broken on join, or maybe in other cases? eh just ignore this for now, test later.
//            player.kick("Invalid latency id, expected=" + (poll == null ? "none" : poll.id()) + ", actual=" + id);
            return;
        }

        poll.run();

        player.getLatencyUtil().onLatencyAccepted(poll);
        event.setCancelled(poll.ours());

        player.getLatencyUtil().prevAcceptedTime = System.currentTimeMillis();
        player.getLatencyUtil().prevAcceptedLatency = poll;
    }
}
