package ac.boar.anticheat.compensated.cache.container.impl;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import lombok.Getter;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;

@Getter
public class TradeContainerCache extends ContainerCache {
    private final NbtMap offers;

    public TradeContainerCache(CompensatedInventory inventory, NbtMap offers, byte id, ContainerType type, Vector3i blockPosition, long uniqueEntityId) {
        super(inventory, id, type, blockPosition, uniqueEntityId);
        this.offers = offers;
    }
}
