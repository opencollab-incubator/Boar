package ac.boar.anticheat.packets.other;

import ac.boar.geyser.util.GeyserUtil;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.geysermc.api.util.BedrockPlatform;
import org.geysermc.geyser.session.GeyserSession;

public class NetworkLatencyPackets implements PacketListener {
    public final static long LATENCY_MAGNITUDE = 1000000L;
    public final static long PS5_LATENCY_MAGNITUDE = 1000L;

    @Override
    public void onPacketReceived(final CloudburstPacketEvent event) {
        if (!(event.getPacket() instanceof NetworkStackLatencyPacket packet)) {
            return;
        }

        final GeyserSession session = event.getPlayer().getSession();
        // Bedrock player have different latency magnitude depending on the platform, however this is the only one we know about
        // TODO: Figure out what the magnitude for PS4 is, currently we only know PS5 (BedrockPlatform.PS4 is an misleading name.)
        long id = packet.getTimestamp() / (session.platform() == BedrockPlatform.PS4 ? PS5_LATENCY_MAGNITUDE : LATENCY_MAGNITUDE);

        // Positive id is for keep alive passthrough hack, and there also only 2 other negative id that we just need to check for.
        // This implementation could be a problem later on considering that Networking API will soon become a thing but oh welp.
        // Let's hope there isn't anything else that will conflict with our anticheat latency system.
        if (id >= 0 || id == GeyserUtil.MAGIC_FORM_IMAGE_HACK_TIMESTAMP) {
            return;
        }

        // There is this weird bug with virtual inventory hack, so uhhh, this is the work around for it
        // TODO: Properly fix Boar to actually resolve this (https://github.com/oryxel1/Boar/issues/29).
        if (id == GeyserUtil.MAGIC_VIRTUAL_INVENTORY_HACK) {
            if (session.getPendingOrCurrentBedrockInventoryId() == -1) { // There is no hack to be done here.
                return;
            }

            if (session.getInventoryHolder() != null) {
                session.getInventoryHolder().pending(true); // Yep.
            }
            return;
        }

        event.getPlayer().getLatencyUtil().confirmStackId(Math.abs(id));
        event.setCancelled(true);
    }
}
