package ac.boar.anticheat.ack.types;

import ac.boar.anticheat.ack.Acknowledgment;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;

public record UpdateTradeAck(byte containerId, ContainerType containerType, NbtMap offers, long traderUniqueEntityId) implements Acknowledgment {
}
