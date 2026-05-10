package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;

public record EntityMetadataAck(long runtimeEntityId, EntityDataMap metadata) implements Acknowledgment {
}
