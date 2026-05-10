package ac.boar.anticheat.packets.player;

import ac.boar.anticheat.ack.types.GlideBoostAck;
import ac.boar.anticheat.ack.types.PromoteVelocityAck;
import ac.boar.anticheat.ack.types.UncertainVelocityAck;
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

        // Yes only this, there no packet for explosion (for bedrock), geyser translate explosion directly to SetEntityMotionPacket
        if (event.getPacket() instanceof SetEntityMotionPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                return;
            }

            // I think there is some rewind like behavior when there is ehm the tick is not 0, so just default back to 0 till I figure it out.
            packet.setTick(0);

            player.sendLatencyStack(new UncertainVelocityAck(new Vec3(packet.getMotion())));
            event.getPostTasks().add(() -> player.sendLatencyStack(new PromoteVelocityAck()));
        }

        if (event.getPacket() instanceof MovementEffectPacket packet) {
            if (packet.getEntityRuntimeId() != player.runtimeEntityId || packet.getEffectType() != MovementEffectType.GLIDE_BOOST) {
                return;
            }

            // If you have rewind history that is not 0 and send tick id 0 this will fucked up the movement~~~:tm:
            // Well anyway.... if you just send a valid tick id or send an invalid id it works fine :D
            packet.setTick(Integer.MIN_VALUE);

            player.sendLatencyStack();
            player.sendLatencyStack(new GlideBoostAck(packet.getDuration()));
        }
    }
}
