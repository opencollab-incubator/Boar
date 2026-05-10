package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.data.effect.Effect;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;

public record MobEffectAck(Effect effect, MobEffectPacket.Event event, int amplifier, int duration) implements Acknowledgment {
}
