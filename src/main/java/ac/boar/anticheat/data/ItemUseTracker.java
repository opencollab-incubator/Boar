package ac.boar.anticheat.data;

import ac.boar.anticheat.player.BoarPlayer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.item.Items;

@RequiredArgsConstructor
@Getter
@Setter
public class ItemUseTracker {
    private final BoarPlayer player;

    private ItemData usedItem = ItemData.AIR;
    private int javaItemId = -1;
    private DirtyUsing dirtyUsing = DirtyUsing.NONE;
    private int useDuration;
    public enum DirtyUsing {
        METADATA, INVENTORY_TRANSACTION, NONE
    }

    public boolean isUsingSpear() {
        return javaItemId == Items.WOODEN_SPEAR.javaId() || javaItemId == Items.STONE_SPEAR.javaId()
                || javaItemId == Items.IRON_SPEAR.javaId() || javaItemId == Items.GOLDEN_SPEAR.javaId() || javaItemId == Items.DIAMOND_SPEAR.javaId() || javaItemId == Items.NETHERITE_SPEAR.javaId();
    }

    public void preTick() {
        if (!player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
            return;
        }

        if (this.javaItemId == Items.TRIDENT.javaId()) {
            player.sinceTridentUse++;
        }
    }

    public void postTick() {
        if (!player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
            if (this.usedItem != ItemData.AIR || this.javaItemId != -1) {
                this.release();
            }

            return;
        }

        if (this.usedItem == ItemData.AIR || this.javaItemId == -1) {
            this.release();
            return;
        }

        if (!player.compensatedInventory.inventoryContainer.getHeldItemData().equals(this.usedItem, false, false, false)) {
            this.release();
        }
    }

    public void release() {
        this.usedItem = ItemData.AIR;
        this.javaItemId = -1;
        player.sinceTridentUse = 0;
        player.getFlagTracker().set(EntityFlag.USING_ITEM, false);
    }

    public void use(final ItemData usedItem, int itemId, boolean skip) {
        if (!canBeUse(usedItem, itemId) && !skip) {
            return;
        }

        this.usedItem = usedItem;
        this.javaItemId = itemId;
        this.dirtyUsing = DirtyUsing.INVENTORY_TRANSACTION;

        player.sinceTridentUse = 0;
    }

    private boolean canBeUse(final ItemData usedItem, int itemId) {
        // This way we can support custom item use duration too, also wrap this since I don't trust myself enough.
        try {
            final NbtMap map = usedItem.getDefinition().getComponentData();
            if (map != null) {
                NbtMap components = map.getCompound("components");
                if (components == null) {
                    return true;
                }

                if (components.containsKey("minecraft:use_duration")) {
                    return true;
                } else {
                    NbtMap itemProperties = components.getCompound("item_properties");
                    if (itemProperties.containsKey("use_duration")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}

        return itemId == Items.BOW.javaId() || itemId == Items.CROSSBOW.javaId() ||
                itemId == Items.TRIDENT.javaId() || itemId == Items.ENDER_EYE.javaId() ||
                itemId == Items.SPYGLASS.javaId() || itemId == Items.OMINOUS_BOTTLE.javaId() || itemId == Items.POTION.javaId();
    }
}
