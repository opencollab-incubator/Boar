package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import ac.boar.anticheat.data.effect.Effect;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;

public record MobEffectAck(Effect effect, MobEffectPacket.Event event, int amplifier, int duration, EntityCache horse, long sentInput,
        long sentInputEpoch) implements Acknowledgment {
    public MobEffectAck(Effect effect, MobEffectPacket.Event event, int amplifier, int duration) {
        this(effect, event, amplifier, duration, null, -1, -1);
    }
}
