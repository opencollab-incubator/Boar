package ac.boar.anticheat.data.inventory;

import ac.boar.anticheat.compensated.cache.container.ContainerCache;

// A slot's item before an ItemStackRequest changed it
public record SlotSnapshot(ContainerCache container, int slot, ItemCache oldItem) {
}
