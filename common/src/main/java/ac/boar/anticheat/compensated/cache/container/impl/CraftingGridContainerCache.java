package ac.boar.anticheat.compensated.cache.container.impl;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;

public class CraftingGridContainerCache extends ContainerCache {

    public static final int FIRST_SLOT = 28;
    public static final int SLOT_COUNT = 4;

    public CraftingGridContainerCache(final CompensatedInventory inventory) {
        super(inventory, (byte) ContainerId.UI, ContainerType.INVENTORY, null, -1L, FIRST_SLOT, SLOT_COUNT);
    }

}
