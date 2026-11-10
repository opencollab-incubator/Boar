package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import ac.boar.anticheat.compensated.cache.entity.EntityCache;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;

import java.util.List;

public record EntityAttributesAck(List<AttributeData> attributes, EntityCache entity) implements Acknowledgment {
}
