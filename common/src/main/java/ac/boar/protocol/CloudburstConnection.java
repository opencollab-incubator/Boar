package ac.boar.protocol;

import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

@RequiredArgsConstructor
public class CloudburstConnection implements BoarConnection {
    private final BedrockServerSession session;

    @Override
    public void sendPacket(BedrockPacket packet) {
        this.session.sendPacket(packet);
    }

    @Override
    public void sendPacketImmediately(BedrockPacket packet) {
        this.session.sendPacketImmediately(packet);
    }

    @Override
    public Channel getChannel() {
        return this.session.getPeer().getChannel();
    }
}
