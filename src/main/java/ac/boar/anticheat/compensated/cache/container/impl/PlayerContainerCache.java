package ac.boar.anticheat.compensated.cache.container.impl;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.data.inventory.ItemCache;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.inventory.GeyserItemStack;

public class PlayerContainerCache extends ContainerCache {
    public PlayerContainerCache(final CompensatedInventory inventory) {
        super(inventory, (byte) ContainerId.INVENTORY, ContainerType.INVENTORY, null, -1L);
    }

    public ItemCache getHeldItemCache() {
        return this.getItemFromSlot(this.inventory.heldItemSlot);
    }

    public ItemData getHeldItemData() {
        return this.getItemFromSlot(this.inventory.heldItemSlot).getData();
    }

    public GeyserItemStack getHeldItem() {
        return GeyserItemStack.from(inventory.getPlayer().getSession(), this.inventory.translate(getHeldItemData()));
    }

    public ItemCache getItemFromSlot(final int slot) {
        if (slot < 0 || slot > 8 || slot >= this.getContainerSize()) {
            return ItemCache.AIR;
        }

        return this.get(slot);
    }
}
