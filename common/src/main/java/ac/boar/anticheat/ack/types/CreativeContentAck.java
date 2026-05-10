package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;

import java.util.List;

public record CreativeContentAck(List<CreativeItemData> contents) implements Acknowledgment {
}
