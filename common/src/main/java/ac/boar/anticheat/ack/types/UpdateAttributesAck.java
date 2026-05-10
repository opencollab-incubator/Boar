package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;

import java.util.List;

public record UpdateAttributesAck(List<AttributeData> attributes) implements Acknowledgment {
}
