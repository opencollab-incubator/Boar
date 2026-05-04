package ac.boar.anticheat.compensated.cache.container;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.data.inventory.ItemCache;
import lombok.Getter;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.Arrays;

public class ContainerCache {
    protected final CompensatedInventory inventory;

    @Getter
    private final byte id;
    @Getter
    private final ContainerType type;
    @Getter
    private final Vector3i blockPosition;
    @Getter
    private final long uniqueEntityId;

    private final int containerSize;
    @Getter
    private final int offset;

    @Getter
    private final ItemCache[] contents;

    public ContainerCache(CompensatedInventory inventory, byte id, ContainerType type, Vector3i blockPosition, long uniqueEntityId) {
        this.inventory = inventory;
        this.id = id;
        this.type = type;
        this.blockPosition = blockPosition;
        this.uniqueEntityId = uniqueEntityId;

        this.offset = switch (type) {
            case ENCHANTMENT -> 14;
            case LOOM -> 9;
            case WORKBENCH -> 32;
            case BEACON -> 27;
            case ANVIL -> 1;
            case STONECUTTER -> 3;
            case CARTOGRAPHY -> 12;
            case SMITHING_TABLE -> 51;
            case GRINDSTONE -> 16;
            case TRADE -> 4;
            default -> 0;
        };
        this.containerSize = switch (type) {
            case FURNACE, BLAST_FURNACE, SMOKER, LOOM, SMITHING_TABLE -> 3;
            case BREWING_STAND, HOPPER, MINECART_HOPPER -> 5;
            case DROPPER, DISPENSER, WORKBENCH, CRAFTER -> 9;
            case ENCHANTMENT, ANVIL, HORSE, CARTOGRAPHY, GRINDSTONE -> 2;
            case BEACON, STONECUTTER -> 1;
            case STRUCTURE_EDITOR, COMMAND_BLOCK -> 0;
            case MINECART_CHEST, CHEST_BOAT -> 26;
            case CONTAINER -> 56; // I'm just going to assume this is a 9x6, uhhh sucks for performance?
            case TRADE -> 2; //  Again, assuming this is 2
            case ARMOR -> 4;
            default -> 36;
        };

        if (this.containerSize > 0) {
            this.contents = new ItemCache[this.containerSize];
            Arrays.fill(this.contents, ItemCache.AIR);
        } else {
            this.contents = null;
        }
    }

    public int getContainerSize() {
        return this.containerSize + this.offset;
    }

    public ItemCache get(final int slot) {
        ItemCache cache = this.contents[slot - this.offset];
        return cache == null ? ItemCache.AIR : cache; // just in case.
    }

    public void set(final int slot, final ItemData raw) {
        this.set(slot, raw, true);
    }

    public void set(final int slot, final ItemCache raw) {
        this.set(slot, raw, true);
    }

    public void set(final int slot, final ItemData raw, final boolean offset) {
        this.set(slot, ItemCache.build(this.inventory, raw), offset);
    }

    public void set(final int slot, final ItemCache cache, final boolean offset) {
        this.contents[(offset ? slot - this.offset : slot)] = cache;
    }
}
