package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

public record InventorySlotAck(int containerId, int slot, ItemData item, ItemData storageItem) implements Acknowledgment {
}
