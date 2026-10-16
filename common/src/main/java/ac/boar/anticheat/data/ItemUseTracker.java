package ac.boar.anticheat.data;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.util.StringUtil;
import ac.boar.mappings.item.Item;
import ac.boar.mappings.item.Items;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Getter
@Setter
public class ItemUseTracker {
    private final BoarPlayer player;

    private ItemData usedItem = ItemData.AIR;
    private Item item;
    private DirtyUsing dirtyUsing = DirtyUsing.NONE;
    private boolean useFromTransaction;
    private int useDuration;

    // Tick the current use started, or -1 if not using. Used to spot the "done eating" click.
    private long useStartTick = -1;
    private boolean wasUsing;

    // Tick the click-air that started this use arrived, and whether the client has since received the server's
    // "using" metadata. Some clients only slow down once that arrives, so we only force the slowdown after it.
    private long transactionStartTick = -1;
    private boolean serverConfirmed;

    public enum DirtyUsing {
        METADATA, INVENTORY_TRANSACTION, NONE
    }

    public boolean isUsingSpear() {
        if (this.item == null) {
            return false;
        }
        return this.item.is(Items.WOODEN_SPEAR) || this.item.is(Items.STONE_SPEAR) || this.item.is(Items.COPPER_SPEAR)
                || this.item.is(Items.IRON_SPEAR) || this.item.is(Items.GOLDEN_SPEAR) || this.item.is(Items.DIAMOND_SPEAR) || this.item.is(Items.NETHERITE_SPEAR);
    }

    public void preTick() {
        final boolean using = this.player.getFlagTracker().has(EntityFlag.USING_ITEM);
        if (using && !this.wasUsing) {
            this.useStartTick = this.player.tick;
        } else if (!using) {
            this.useStartTick = -1;
        }
        this.wasUsing = using;

        if (!using) {
            return;
        }

        if (this.item != null && this.item.is(Items.TRIDENT)) {
            this.player.sinceTridentUse++;
        }
    }

    public void postTick() {
        if (!this.player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
            if (this.usedItem != ItemData.AIR || this.item != null) {
                this.release();
            }

            return;
        }

        if (this.usedItem == ItemData.AIR || this.item == null) {
            return;
        }

        if (this.player.compensatedInventory.inventoryContainer.getHeldItemCache().isEmpty()) {
            return;
        }

        if (!this.player.compensatedInventory.inventoryContainer.getHeldItemData().equals(this.usedItem, false, false, false)) {
            this.release();
        }
    }

    public void release() {
        this.useStartTick = -1;
        this.wasUsing = false;
        this.transactionStartTick = -1;
        this.serverConfirmed = false;

        this.useFromTransaction = false;
        this.player.lastItemUseStateChangeTick = this.player.tick;
        this.usedItem = ItemData.AIR;
        this.item = null;
        this.player.sinceTridentUse = 0;
        this.player.getFlagTracker().set(EntityFlag.USING_ITEM, false);
    }

    public void use(final ItemData usedItem, Item item, boolean skip) {
        if (!canBeUsed(usedItem, item) && !skip) {
            return;
        }

        this.useFromTransaction = !skip || this.dirtyUsing == DirtyUsing.INVENTORY_TRANSACTION;
        this.player.lastItemUseStateChangeTick = this.player.tick;
        this.usedItem = usedItem;
        this.item = item;
        this.dirtyUsing = DirtyUsing.INVENTORY_TRANSACTION;

        player.sinceTridentUse = 0;
    }

    public boolean startFromTransaction(final ItemData usedItem, final Item item) {
        if (!canBeUsed(usedItem, item)) {
            return false;
        }

        final String identifier = identifier(usedItem);
        if (identifier != null && CONSUMABLE_IDENTIFIERS.contains(identifier) && !identifier.equals("shield") && !ALWAYS_EDIBLE.contains(identifier) && this.player.hunger >= 20 && this.player.gameType != GameType.CREATIVE) {
            return false;
        }

        this.transactionStartTick = this.player.tick;
        this.serverConfirmed = false;
        this.use(usedItem, item, false);
        this.dirtyUsing = DirtyUsing.NONE;
        this.player.getFlagTracker().set(EntityFlag.USING_ITEM, true);
        return true;
    }

    public void onServerUsingAcked(final long sentTick) {
        if (this.transactionStartTick >= 0 && sentTick >= this.transactionStartTick) {
            this.serverConfirmed = true;
        }
    }

    public boolean isSlowdownConfirmed() {
        return this.serverConfirmed;
    }

    public boolean canBeUsed(final ItemData usedItem, Item item) {
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
                }

                if (components.getCompound("minecraft:use_modifiers").containsKey("use_duration")) {
                    return true;
                }

                if (components.getCompound("item_properties").containsKey("use_duration")) {
                    return true;
                }
            }
        } catch (Exception e) {
            Boar.getInstance().getPlatform().logger().error(player.getSession().name() + ": failed to read use_duration components", e);
        }

        // some items may not always carry a readable use_duration component?
        if (isKnownConsumable(usedItem)) {
            return true;
        }

        return item.is(Items.BOW) || item.is(Items.CROSSBOW) ||
                item.is(Items.TRIDENT) || item.is(Items.ENDER_EYE) ||
                item.is(Items.SPYGLASS) || item.is(Items.OMINOUS_BOTTLE) || item.is(Items.POTION);
    }

    private static final Map<String, Integer> CONSUME_TICKS = Map.of(
            "dried_kelp", 16,
            "honey_bottle", 40
    );
    private static final int DEFAULT_CONSUME_TICKS = 32;

    public boolean isFinishedConsuming(final ItemData heldItem) {
        if (heldItem == null || !heldItem.equals(this.usedItem, false, false, false)) {
            return false;
        }
        return this.isPastConsumeDuration();
    }

    public boolean isPastConsumeDuration() {
        if (this.useStartTick < 0 || this.item == null || !this.player.getFlagTracker().has(EntityFlag.USING_ITEM)) {
            return false;
        }

        final String identifier = identifier(this.usedItem);
        final boolean consumable = identifier != null && !identifier.equals("shield") && (CONSUMABLE_IDENTIFIERS.contains(identifier) || this.item.is(Items.POTION) || this.item.is(Items.OMINOUS_BOTTLE));
        if (!consumable) {
            return false;
        }

        final int duration = CONSUME_TICKS.getOrDefault(identifier, DEFAULT_CONSUME_TICKS);
        return this.player.tick - this.useStartTick >= duration - 1;
    }

    private static String identifier(final ItemData item) {
        try {
            final String identifier = item.getDefinition().getIdentifier();
            return identifier == null ? null : StringUtil.sanitizePrefix(identifier);
        } catch (Exception e) {
            return null;
        }
    }

    // Can be eaten/drunk even with full hunger.
    private static final Set<String> ALWAYS_EDIBLE = Set.of(
            "golden_apple", "enchanted_golden_apple", "chorus_fruit", "honey_bottle", "suspicious_stew", "milk_bucket"
    );

    private static final java.util.Set<String> CONSUMABLE_IDENTIFIERS = java.util.Set.of(
            "milk_bucket", "honey_bottle", "suspicious_stew", "mushroom_stew", "rabbit_stew", "beetroot_soup",
            "apple", "golden_apple", "enchanted_golden_apple", "melon_slice", "sweet_berries", "glow_berries",
            "chorus_fruit", "dried_kelp", "cookie", "bread", "carrot", "golden_carrot", "potato", "baked_potato",
            "poisonous_potato", "beetroot", "pumpkin_pie", "spider_eye", "rotten_flesh", "pufferfish",
            "tropical_fish", "cod", "cooked_cod", "salmon", "cooked_salmon", "beef", "cooked_beef", "porkchop",
            "cooked_porkchop", "chicken", "cooked_chicken", "mutton", "cooked_mutton", "rabbit", "cooked_rabbit",
            "shield"
    );

    private boolean isKnownConsumable(final ItemData usedItem) {
        try {
            final String identifier = usedItem.getDefinition().getIdentifier();
            if (identifier == null) {
                return false;
            }
            return CONSUMABLE_IDENTIFIERS.contains(StringUtil.sanitizePrefix(identifier));
        } catch (Exception e) {
            return false;
        }
    }
}
