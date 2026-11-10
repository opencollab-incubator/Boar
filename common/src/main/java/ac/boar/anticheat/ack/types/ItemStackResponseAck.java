package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;

import java.util.List;

public record ItemStackResponseAck(List<ItemStackResponse> responses) implements Acknowledgment {
}
