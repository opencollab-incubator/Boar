package ac.boar.anticheat.data.input;

import ac.boar.anticheat.data.EntityDimensions;
import ac.boar.anticheat.data.vanilla.AttributeInstance;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.EnumSet;
import java.util.Map;

public record TickData(PlayerAuthInputPacket packet, EnumSet<EntityFlag> flags,
                       Map<String, AttributeInstance> attributes,
                       EntityDimensions dimensions) {
}
