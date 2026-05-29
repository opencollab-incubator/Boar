package ac.boar.protocol;

import io.netty.channel.Channel;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

/**
 * The outbound transport Boar uses to reach a single Bedrock client. Boar only ever needs to send
 * packets and reach the underlying netty channel (to flush and to install its pipeline handlers),
 * so this is intentionally narrower than a full {@code BedrockServerSession}.
 * Abstracting it this way lets a player be backed by something other than a cloudburst
 * {@code BedrockServerSession} — e.g. a custom proxy-fed connection that writes packets straight to a
 * channel and has no session object at all.
 */
public interface BoarConnection {

    void sendPacket(BedrockPacket packet);

    void sendPacketImmediately(BedrockPacket packet);

    Channel getChannel();
}
