package ac.boar.anticheat.data.input;

import ac.boar.anticheat.data.EntityDimensions;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.EnumSet;

public record TickData(PlayerAuthInputPacket packet, EnumSet<EntityFlag> flags, EntityDimensions dimensions) {
}
