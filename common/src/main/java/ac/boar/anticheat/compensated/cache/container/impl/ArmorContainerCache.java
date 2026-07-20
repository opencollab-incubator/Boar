package ac.boar.anticheat.compensated.cache.container.impl;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.data.enchantment.Enchantment;
import ac.boar.anticheat.data.inventory.ItemCache;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;

import java.util.Map;

public class ArmorContainerCache extends ContainerCache {
    public ArmorContainerCache(CompensatedInventory inventory) {
        super(inventory, (byte) ContainerId.ARMOR, ContainerType.INVENTORY, null, -1L);
    }

    @Override
    public void set(int slot, ItemCache cache, boolean offset) {
        if (slot == 2) { // Accounting for swift sneak.
            ItemCache old = this.get(2);
            Map<Enchantment, Integer> oldEnchantments = CompensatedInventory.getEnchantments(old.getData());
            Map<Enchantment, Integer> newEnchantments = CompensatedInventory.getEnchantments(cache.getData());

            if (!newEnchantments.containsKey(Enchantment.SWIFT_SNEAK)) {
                inventory.getPlayer().sneakingAttributeModifier = 0;
            } else {
                // If the old item have swift sneak, new item also have swift sneak, then the attribute won't change (even if it's 1->20 lmao)
                // So only changes this if the player goes from non-swift sneak item to a swift sneak enchanted item.
                if (!oldEnchantments.containsKey(Enchantment.SWIFT_SNEAK)) {
                    Integer level = newEnchantments.get(Enchantment.SWIFT_SNEAK);
                    inventory.getPlayer().sneakingAttributeModifier = 0.15f + 0.15f * (float)(level - 1);
                }
            }
        }

        super.set(slot, cache, offset);
    }
}
