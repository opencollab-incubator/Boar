package ac.boar.anticheat.packets.player;

import ac.boar.anticheat.ack.types.GlideBoostAck;
import ac.boar.anticheat.ack.types.VelocityAck;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.math.Vec3;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.data.MovementEffectType;
import org.cloudburstmc.protocol.bedrock.packet.MovementEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;

public class PlayerVelocityPackets implements PacketListener {
    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        switch (event.getPacket()) {
            case SetEntityMotionPacket packet when packet.getRuntimeEntityId() == player.runtimeEntityId -> {
                player.sendLatencyStack(new VelocityAck(new Vec3(packet.getMotion())));
            }
            case MovementEffectPacket packet -> {
                if (packet.getEntityRuntimeId() != player.runtimeEntityId || packet.getEffectType() != MovementEffectType.GLIDE_BOOST) {
                    return;
                }

                // If you have rewind history that is not 0 and send tick id 0 this will fucked up the movement~~~:tm:
                // Well anyway.... if you just send a valid tick id or send an invalid id it works fine :D
                packet.setTick(Integer.MIN_VALUE);
                player.sendLatencyStack(new GlideBoostAck(packet.getDuration()));
            }
            default -> {}
        }
    }
}
