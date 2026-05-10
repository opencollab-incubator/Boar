package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;

public record ContainerOpenAck(byte id, ContainerType type, Vector3i blockPosition, long uniqueEntityId) implements Acknowledgment {
}
