package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.List;

public record InventoryContentAck(int containerId, List<ItemData> contents, ItemData storageItem) implements Acknowledgment {
}
