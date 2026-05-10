package ac.boar.anticheat.packets.player;

import ac.boar.anticheat.ack.types.MobEffectAck;
import ac.boar.anticheat.data.effect.Effect;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.protocol.api.CloudburstPacketEvent;
import ac.boar.protocol.api.PacketListener;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;

public class PlayerEffectPackets implements PacketListener {
    @Override
    public void onPacketSend(final CloudburstPacketEvent event) {
        final BoarPlayer player = event.getPlayer();

        if (event.getPacket() instanceof MobEffectPacket packet) {
            if (packet.getRuntimeEntityId() != player.runtimeEntityId) {
                return;
            }

            Effect effect = Effect.byId(packet.getEffectId());
            if (effect == null) {
                return;
            }

            player.sendLatencyStack(new MobEffectAck(effect, packet.getEvent(), packet.getAmplifier(), packet.getDuration()));
        }
    }
}
