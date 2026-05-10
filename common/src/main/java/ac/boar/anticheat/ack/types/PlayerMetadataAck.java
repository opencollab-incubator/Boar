package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;

import java.util.Set;

public record PlayerMetadataAck(Float width, Float height, Float scale, Set<EntityFlag> flags) implements Acknowledgment {
}
