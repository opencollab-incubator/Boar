package ac.boar.anticheat.packets.server;

import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;

public final class ServerRewindPackets implements PacketListener {
    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        switch (event.getPacket()) {
            case MobEffectPacket packet -> packet.setTick(0L);
            case MovePlayerPacket packet -> packet.setTick(0L);
            case SetEntityDataPacket packet -> packet.setTick(0L);
            case SetEntityMotionPacket packet -> packet.setTick(0L);
            case UpdateAttributesPacket packet -> packet.setTick(0L);
            default -> {}
        }
    }
}
